import { expect, Page, test } from '@playwright/test';

import { administrador, iniciarSesion, revisarAccesibilidad, vigilarPoliticaDeContenido } from './apoyo';

// HU-23: la venta del mostrador, en orden y con una sola sesión del Administrador (que también vende): arma la venta
// de un consumidor final, cobra en efectivo, ve el comprobante, la encuentra en «Pedidos» con su canal y la anula
test.describe.configure({ mode: 'serial' });

let page: Page;
let numeroDeVenta: string;
let bloqueos: string[];
// Lo disponible del martillo antes de vender: al anular, el stock vuelve a eso
let disponibleAntes: string;

test.beforeAll(async ({ browser }) => {
  // En un contexto propio: axe abre otra página en el mismo contexto
  page = await (await browser.newContext()).newPage();
  bloqueos = vigilarPoliticaDeContenido(page);
  const { correo, contrasena } = administrador();
  await iniciarSesion(page, correo, contrasena);
});

test.afterEach(() => {
  expect(bloqueos).toEqual([]);
});

test.afterAll(async () => {
  await page.context().close();
});

test('arma la venta con el disponible de cada producto', async () => {
  await page.getByRole('link', { name: 'Nueva venta' }).first().click();
  await expect(page.getByRole('heading', { level: 1, name: 'Nueva venta' })).toBeVisible();

  await page.getByLabel('Buscar producto').fill('martillo');
  // El de la búsqueda: los botones de la línea también nombran al producto
  const resultado = page.locator('button.resultado').filter({ hasText: 'Martillo de uña' });
  await expect(resultado).toContainText('Disponible');
  disponibleAntes = (await resultado.locator('.disponible').textContent())!.trim();
  await revisarAccesibilidad(page);

  await resultado.click();
  await resultado.click();
  await expect(page.getByLabel('Cantidad de Martillo de uña 16 oz')).toHaveValue('2');
  await expect(page.locator('.total')).toContainText('171,00');
  await revisarAccesibilidad(page);
});

test('cobra en efectivo con el cambio y muestra el comprobante', async () => {
  await page.getByLabel('Recibido en efectivo (Bs)').fill('200');
  await expect(page.locator('#cambio')).toContainText('29,00');

  await page.getByRole('button', { name: 'Cobrar en efectivo' }).click();

  await expect(page).toHaveURL(/\/ventas\/\d+$/);
  numeroDeVenta = page.url().split('/').pop()!;
  const ticket = page.locator('.ticket');
  await expect(ticket).toContainText(`Venta N.º ${numeroDeVenta}`);
  await expect(ticket).toContainText('Consumidor final');
  await expect(ticket).toContainText('No válido como factura');
  await expect(ticket.locator('tfoot')).toContainText('29,00');
  await revisarAccesibilidad(page);
});

test('la venta aparece en «Pedidos» con su canal', async () => {
  await page.goto('/pedidos?estado=TODOS&canal=TIENDA');

  const fila = page.getByRole('link', { name: new RegExp(`N\\.º\\s${numeroDeVenta}`) });
  await expect(fila).toContainText('En tienda');
  await revisarAccesibilidad(page);

  await fila.click();
  await expect(page.getByRole('heading', { level: 2, name: 'Venta en tienda' })).toBeVisible();
  await expect(page.getByText('Consumidor final')).toBeVisible();
  await revisarAccesibilidad(page);
});

test('el Administrador la anula con un motivo y el stock vuelve', async () => {
  await page.getByRole('link', { name: 'Ver el comprobante' }).click();
  await page.getByRole('button', { name: 'Anular venta' }).click();
  const dialogo = page.getByRole('dialog');
  await expect(dialogo).toBeVisible();
  await revisarAccesibilidad(page);

  await dialogo.getByLabel('Motivo').fill('Producto equivocado');
  await dialogo.getByRole('button', { name: 'Anular venta' }).click();

  await expect(page.locator('.ticket .estado')).toHaveText('Anulado');
  await expect(page.locator('.ticket')).toContainText('Producto equivocado');
  await expect(page.getByRole('button', { name: 'Anular venta' })).toHaveCount(0);

  await page.goto('/ventas/nueva');
  await page.getByLabel('Buscar producto').fill('martillo');
  await expect(page.locator('button.resultado').filter({ hasText: 'Martillo de uña' }).locator('.disponible')).toHaveText(
    disponibleAntes,
  );
});
