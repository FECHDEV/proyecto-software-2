import { expect, test } from '@playwright/test';

// El visitante sin cuenta: el catálogo de prueba tiene 3 productos y la cinta métrica está agotada
test.describe('Sin sesión', () => {
  test('muestra los productos y marca el agotado', async ({ page }) => {
    await page.goto('/catalogo');

    const tarjetas = page.locator('app-tarjeta-producto');
    await expect(tarjetas).toHaveCount(3);
    await expect(page.getByText('3 productos')).toBeVisible();
    await expect(tarjetas.filter({ hasText: 'Cinta métrica 5 m' })).toContainText('Agotado');
    await expect(tarjetas.filter({ hasText: 'Pala de punta' })).not.toContainText('Agotado');
  });

  test('la búsqueda filtra el catálogo y queda en la dirección', async ({ page }) => {
    await page.goto('/');
    const buscar = page.getByRole('search', { name: 'Tienda' });
    await buscar.getByRole('searchbox').fill('pala');
    await buscar.getByRole('button', { name: 'Buscar' }).click();

    await expect(page).toHaveURL(/\/catalogo\?q=pala/);
    await expect(page.locator('app-tarjeta-producto')).toHaveCount(1);
    await expect(page.locator('app-tarjeta-producto')).toContainText('Pala de punta');
  });

  test('el producto muestra su precio y, para agregarlo, pide iniciar sesión', async ({ page }) => {
    await page.goto('/catalogo');
    await page.locator('app-tarjeta-producto').filter({ hasText: 'Martillo de uña 16 oz' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'Martillo de uña 16 oz' })).toBeVisible();
    await expect(page.locator('.precio').first()).toContainText('85,50');
    await expect(page.getByText('Disponible', { exact: true })).toBeVisible();

    const producto = page.url();
    await page.getByRole('button', { name: 'Agregar al carrito' }).click();
    await expect(page).toHaveURL(/\/inicio-sesion\?destino=/);
    expect(decodeURIComponent(page.url())).toContain(new URL(producto).pathname);
  });

  test('con la contraseña equivocada avisa y no entra', async ({ page }) => {
    await page.goto('/inicio-sesion');
    await page.getByLabel('Correo').fill('nadie@tienda.test');
    await page.getByLabel('Contraseña', { exact: true }).fill('no-es-la-clave');
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page).toHaveURL(/\/inicio-sesion/);
    await expect(page.getByRole('link', { name: 'Iniciar sesión' })).toBeVisible();
  });
});
