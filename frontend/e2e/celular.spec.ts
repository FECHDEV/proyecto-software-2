import { expect, Page, test } from '@playwright/test';

import { administrador, clienteNuevo, iniciarSesion, registrar, revisarAccesibilidad } from './apoyo';

// En un celular (proyecto «celular» de playwright.config.ts): la tienda entra en el ancho y se puede comprar
async function sinScrollHorizontal(page: Page): Promise<void> {
  const desborde = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(desborde).toBeLessThanOrEqual(0);
}

test('el catálogo y el producto entran en el ancho del celular', async ({ page }) => {
  await page.goto('/catalogo');
  await expect(page.locator('app-tarjeta-producto')).toHaveCount(3);
  await sinScrollHorizontal(page);
  await revisarAccesibilidad(page);

  await page.locator('app-tarjeta-producto').filter({ hasText: 'Pala de punta' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Pala de punta' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Agregar al carrito' })).toBeVisible();
  await sinScrollHorizontal(page);
  await revisarAccesibilidad(page);
});

test('desde el celular se registra y agrega al carrito', async ({ page }) => {
  await registrar(page, clienteNuevo());

  await page.goto('/catalogo');
  await page.locator('app-tarjeta-producto').filter({ hasText: 'Pala de punta' }).click();
  await page.getByRole('button', { name: 'Agregar al carrito' }).click();
  await expect(page.getByText('Se agregó al carrito.')).toBeVisible();

  await page.getByRole('link', { name: 'Ver carrito' }).click();
  await expect(page.locator('.linea')).toHaveCount(1);
  await expect(page.getByText('Verifique su correo para confirmar el pedido.')).toBeVisible();
  await sinScrollHorizontal(page);
});

// HU-23: en el mostrador también se vende desde un celular: «Nueva venta» y el comprobante entran en el ancho
test('desde el celular se vende en el mostrador', async ({ page }) => {
  const { correo, contrasena } = administrador();
  await iniciarSesion(page, correo, contrasena);

  await page.goto('/ventas/nueva');
  await page.getByLabel('Buscar producto').fill('martillo');
  await page.locator('button.resultado').filter({ hasText: 'Martillo de uña' }).click();
  await expect(page.locator('.linea')).toHaveCount(1);
  await sinScrollHorizontal(page);
  await revisarAccesibilidad(page);

  await page.getByLabel('Recibido en efectivo (Bs)').fill('100');
  await page.getByRole('button', { name: 'Cobrar en efectivo' }).click();
  await expect(page.locator('.ticket')).toContainText('No válido como factura');
  await sinScrollHorizontal(page);
  await revisarAccesibilidad(page);
});

