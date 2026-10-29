import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

import { expect, Page } from '@playwright/test';

// Lo que comparten las pruebas de extremo a extremo: el entorno que generó e2e/entorno.mjs, las cuentas y el
// correo simulado
const ARCHIVO_ENTORNO = join(__dirname, '.env.e2e');

function entorno(): Record<string, string> {
  const valores: Record<string, string> = {};
  for (const linea of readFileSync(ARCHIVO_ENTORNO, 'utf8').split(/\r?\n/)) {
    const igual = linea.indexOf('=');
    if (igual > 0 && !linea.startsWith('#')) {
      valores[linea.slice(0, igual)] = linea.slice(igual + 1);
    }
  }
  return valores;
}

export function administrador(): { correo: string; contrasena: string } {
  const valores = entorno();
  return { correo: valores['TIENDA_ADMIN_CORREO'], contrasena: valores['TIENDA_ADMIN_PASSWORD'] };
}

export interface Cliente {
  nombre: string;
  apellido: string;
  correo: string;
  contrasena: string;
}

// Un correo nuevo en cada corrida: la base se vacía al levantar, pero una prueba repetida no choca con la anterior
export function clienteNuevo(): Cliente {
  const marca = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  return { nombre: 'Rosa', apellido: 'Pruebas', correo: `rosa.${marca}@tienda.test`, contrasena: `clave-${marca}` };
}

export async function registrar(page: Page, cliente: Cliente): Promise<void> {
  await page.goto('/registro');
  await page.getByLabel('Nombre').fill(cliente.nombre);
  await page.getByLabel('Apellido').fill(cliente.apellido);
  await page.getByLabel('Correo').fill(cliente.correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(cliente.contrasena);
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
}

export async function iniciarSesion(page: Page, correo: string, contrasena: string): Promise<void> {
  await page.goto('/inicio-sesion');
  await page.getByLabel('Correo').fill(correo);
  await page.getByLabel('Contraseña', { exact: true }).fill(contrasena);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Cerrar sesión' })).toBeVisible();
}

// El simulador de correo escribe el token en el log de la API (NotificadorCorreoSimulado): el último para ese correo,
// en las últimas líneas. «Verificaci.n», por si la consola de la imagen no escribe la «ó» en UTF-8
export async function enlaceDeVerificacion(correo: string): Promise<string> {
  const literal = correo.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const patron = new RegExp(`Verificaci.n del correo para .* <${literal}>\\. Token: (\\S+)`, 'g');
  let token: string | undefined;
  await expect
    .poll(
      () => {
        const log = execFileSync(
          'docker',
          ['compose', '-p', 'tienda-e2e', '-f', join(__dirname, '..', '..', 'docker-compose.yml'),
            '--env-file', ARCHIVO_ENTORNO, 'logs', '--no-log-prefix', '--tail', '500', 'backend'],
          { encoding: 'utf8' },
        );
        token = [...log.matchAll(patron)].at(-1)?.[1];
        return token;
      },
      { message: `el correo de verificación de ${correo}`, timeout: 15_000 },
    )
    .toBeTruthy();
  return `/verificacion?token=${encodeURIComponent(token!)}`;
}

// Lo que la política de contenido (nginx, scripts/politica-de-contenido.mjs) bloqueó en la página, en todas sus
// navegaciones: el navegador lo anota en la consola
export function vigilarPoliticaDeContenido(page: Page): string[] {
  const bloqueos: string[] = [];
  page.on('console', (mensaje) => {
    if (mensaje.type() === 'error' && mensaje.text().includes('Content Security Policy')) {
      bloqueos.push(mensaje.text());
    }
  });
  return bloqueos;
}
