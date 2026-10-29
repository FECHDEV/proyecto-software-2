import { expect, Page, test } from '@playwright/test';

import { clienteNuevo, registrar, revisarAccesibilidad } from './apoyo';

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
