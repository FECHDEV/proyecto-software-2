import { defineConfig, devices } from '@playwright/test';

// Pruebas de extremo a extremo del camino principal (decisiones.md → Decisiones técnicas del frontend, 14/10/2026),
// contra la tienda completa en Docker. npm run e2e la levanta desde cero y después corre las pruebas: cada corrida
// crea cuentas, y el backend deja 5 por hora desde una misma conexión (LimiteRegistros). En la PC con Edge, que ya
// está instalado; en el CI, con el Chromium de Playwright
const navegador = process.env['CI'] ? {} : { channel: 'msedge' };

export default defineConfig({
  testDir: './e2e',
  // Las pruebas de un archivo siguen una misma compra, en orden; los archivos usan cuentas distintas
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:8090',
    locale: 'es-BO',
    timezoneId: 'America/La_Paz',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'escritorio', use: { ...devices['Desktop Chrome'], ...navegador }, testIgnore: /celular/ },
    { name: 'celular', use: { ...devices['Pixel 7'], ...navegador }, testMatch: /celular/ },
  ],
});
