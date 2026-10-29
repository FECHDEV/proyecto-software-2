import { expect, Page, test } from '@playwright/test';

import {
  administrador,
  clienteNuevo,
  enlaceDeVerificacion,
  iniciarSesion,
  registrar,
  vigilarPoliticaDeContenido,
} from './apoyo';

// El camino principal, en orden y con una sola sesión: se registra, arma el carrito, verifica el correo, confirma,
// paga con el simulador y ve el pedido; al final, el personal lo entrega
test.describe.configure({ mode: 'serial' });

const cliente = clienteNuevo();
let page: Page;
let numeroDePedido: string;
let bloqueos: string[];

test.beforeAll(async ({ browser }) => {
  page = await browser.newPage();
  bloqueos = vigilarPoliticaDeContenido(page);
});

// Ningún paso de la compra choca con la política de contenido
test.afterEach(() => {
  expect(bloqueos).toEqual([]);
});

test.afterAll(async () => {
  await page.close();
});

test('se registra y queda con la sesión iniciada', async () => {
  await registrar(page, cliente);

  await expect(page.getByRole('link', { name: `Mis datos de ${cliente.nombre}` })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Carrito, sin productos' })).toBeVisible();
});

test('agrega dos productos y el carrito los cuenta', async () => {
  for (const nombre of ['Martillo de uña 16 oz', 'Pala de punta']) {
    await page.goto('/catalogo');
    await page.locator('app-tarjeta-producto').filter({ hasText: nombre }).click();
    await expect(page.getByRole('heading', { level: 1, name: nombre })).toBeVisible();
    await page.getByRole('button', { name: 'Agregar al carrito' }).click();
    await expect(page.getByText('Se agregó al carrito.')).toBeVisible();
  }

  await expect(page.getByRole('link', { name: 'Carrito, 2 productos' })).toBeVisible();
});

test('en el carrito cambia la cantidad, quita un producto y el total sigue', async () => {
  await page.getByRole('link', { name: 'Carrito, 2 productos' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Carrito' })).toBeVisible();
  const total = page.locator('.total strong');
  await expect(total).toContainText('205,50');

  await page.getByRole('button', { name: 'Sumar uno a Martillo de uña 16 oz' }).click();
  await expect(total).toContainText('291,00');

  await page.getByRole('button', { name: 'Quitar Pala de punta' }).click();
  await expect(page.locator('.linea')).toHaveCount(1);
  await expect(total).toContainText('171,00');
});

test('sin verificar el correo no puede confirmar el pedido', async () => {
  await expect(page.getByText('Verifique su correo para confirmar el pedido.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Confirmar pedido' })).toHaveCount(0);
});

test('verifica el correo con el enlace que recibió', async () => {
  await page.goto(await enlaceDeVerificacion(cliente.correo));

  await expect(page.getByText('Su correo quedó verificado. Ya puede confirmar sus pedidos.')).toBeVisible();
});

test('confirma el pedido y tiene 30 minutos para pagarlo', async () => {
  await page.goto('/carrito');
  await page.getByRole('link', { name: 'Confirmar pedido' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Confirmar pedido' })).toBeVisible();

  // El nombre viene de la cuenta; el teléfono lo escribe
  await expect(page.getByLabel('Nombre')).not.toHaveValue('');
  await page.getByLabel('Teléfono').fill('71234567');
  await page.getByRole('button', { name: 'Confirmar pedido' }).click();

  const titulo = page.getByRole('heading', { level: 1, name: /^Pedido N\.º\s\d+$/ });
  await expect(titulo).toBeVisible();
  numeroDePedido = (await titulo.textContent())!.replace(/\D/g, '');
  await expect(page.getByText('Pendiente de pago', { exact: false }).first()).toBeVisible();
  await expect(page.getByRole('timer')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Carrito, sin productos' })).toBeVisible();
});

test('paga con el simulador y el pedido queda pagado', async () => {
  await page.getByRole('button', { name: 'Pagar con QR' }).click();
  await expect(page.getByRole('img', { name: `Código QR para pagar el pedido N.º ${numeroDePedido}` })).toBeVisible();

  await page.getByRole('button', { name: 'Simular pago' }).click();

  await expect(page.getByText('Ya puede retirar su pedido en la tienda.')).toBeVisible();
  await expect(page.locator('.cabecera .estado')).toHaveText('Pagado');
});

test('el pedido aparece pagado en «Mis pedidos»', async () => {
  await page.getByRole('link', { name: 'Mis pedidos' }).first().click();

  const fila = page.getByRole('link', { name: new RegExp(`Pedido N\\.º\\s${numeroDePedido}`) });
  await expect(fila).toContainText('Pagado');
  await expect(fila).toContainText('171,00');
  await expect(page.getByRole('region', { name: /esperando el pago/ })).toHaveCount(0);
});

test('el personal ve el pedido pagado y registra la entrega', async () => {
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  const { correo, contrasena } = administrador();
  await iniciarSesion(page, correo, contrasena);

  await page.goto('/pedidos');
  await page.getByRole('link', { name: new RegExp(`N\\.º\\s${numeroDePedido}`) }).click();
  await expect(page.getByRole('heading', { level: 1, name: new RegExp(`${numeroDePedido}$`) })).toBeVisible();
  await expect(page.getByText(cliente.correo)).toBeVisible();

  await page.getByRole('button', { name: 'Registrar entrega' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /entrega/i }).click();

  await expect(page.locator('.estado').first()).toHaveText('Entregado');
});

test('el Cliente ve su pedido entregado', async () => {
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await iniciarSesion(page, cliente.correo, cliente.contrasena);

  await page.goto(`/mis-pedidos/${numeroDePedido}`);
  await expect(page.getByText(/Lo retiraron/)).toBeVisible();
});
