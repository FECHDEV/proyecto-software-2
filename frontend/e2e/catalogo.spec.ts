import { expect, test } from '@playwright/test';

import { MARCA } from '../src/app/core/marca';

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

// Visibilidad (plan 2026-10-28): lo que leen los buscadores y las tarjetas de los enlaces
test.describe('Buscadores y enlaces compartidos', () => {
  test('robots.txt es el archivo y no la aplicación', async ({ request }) => {
    const respuesta = await request.get('/robots.txt');

    expect(respuesta.status()).toBe(200);
    expect(respuesta.headers()['content-type']).toContain('text/plain');
    expect(await respuesta.text()).toContain('Disallow: /api/');
  });

  test('la tarjeta del enlace trae la imagen con la dirección completa', async ({ request }) => {
    // Como un bot de vista previa: el HTML tal cual, sin ejecutar JavaScript
    const html = await (await request.get('/producto/1')).text();

    // TIENDA_SITIO_URL del entorno (entorno.mjs), no el Host de la petición
    expect(html).toContain('<meta property="og:image" content="http://localhost:8090/compartir.jpg">');
    expect(html).not.toContain('__ORIGEN__');
    // index.html repite la marca para los bots que no ejecutan JavaScript: tiene que coincidir con core/marca.ts
    expect(html).toContain(`<meta name="description" content="${MARCA.descripcion}">`);
    expect(html).toContain(`<meta property="og:description" content="${MARCA.descripcion}">`);
    expect(html).toContain(`<meta property="og:title" content="${MARCA.nombre}">`);
    expect((await request.get('/compartir.jpg')).headers()['content-type']).toBe('image/jpeg');
  });
});
