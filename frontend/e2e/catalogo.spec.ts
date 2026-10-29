import { expect, test } from '@playwright/test';

import { MARCA } from '../src/app/core/marca';
import { vigilarPoliticaDeContenido } from './apoyo';

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

// Política de contenido (decisiones.md → «Política de contenido»): la tienda anda con ella y un script ajeno no corre
test.describe('Política de contenido', () => {
  test('llega con la página y no bloquea nada de la tienda', async ({ page }) => {
    const bloqueos = vigilarPoliticaDeContenido(page);

    const respuesta = await page.goto('/');
    expect(respuesta?.headers()['content-security-policy']).toContain("script-src 'self' 'sha256-");
    await page.goto('/catalogo');
    await page.locator('app-tarjeta-producto').filter({ hasText: 'Martillo de uña 16 oz' }).click();
    // El visor 3D se descarga aparte, en su propio fragmento
    await page.getByRole('button', { name: 'Ver en 3D' }).click();
    await expect(page.locator('model-viewer')).toBeAttached();
    // El script del tema, en línea en index.html, lo permite su hash: si no, la consola anota el bloqueo
    await page.evaluate(() => localStorage.setItem('app.tema', 'claro'));
    await page.goto('/inicio-sesion');
    await expect(page.locator('html')).toHaveAttribute('data-tema', 'claro');
    // La hoja de estilos se activa con el script en línea que agrega Angular
    await expect(page.locator('link[rel="stylesheet"]').first()).toHaveAttribute('media', 'all');

    expect(bloqueos).toEqual([]);
  });

  test('un script inyectado en la página no corre', async ({ page }) => {
    await page.goto('/catalogo');

    const corrio = await page.evaluate(async () => {
      const ventana = window as unknown as Record<string, boolean>;
      const script = document.createElement('script');
      script.textContent = 'window.inyectado = true';
      document.body.append(script);
      const caja = document.createElement('div');
      caja.innerHTML = '<img src="x" onerror="window.inyectadoEnAtributo = true">';
      document.body.append(caja);
      await new Promise((listo) => setTimeout(listo, 500));
      return Boolean(ventana['inyectado'] || ventana['inyectadoEnAtributo']);
    });

    expect(corrio).toBe(false);
  });
});
