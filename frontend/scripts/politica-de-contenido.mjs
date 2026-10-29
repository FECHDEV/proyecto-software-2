// Arma la cabecera Content-Security-Policy de la tienda para nginx (decisiones.md → «Política de contenido»).
// Corre en el Dockerfile después de ng build: lee el index.html compilado y permite sus scripts en línea por su
// hash, así la política coincide siempre con lo que se sirve. Hay dos: el del tema (src/index.html) y el que
// agrega Angular para cargar la hoja de estilos sin bloquear la página.
//
// Uso: node scripts/politica-de-contenido.mjs dist/frontend/browser/index.html > politica-de-contenido.conf

import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';

export function politicaDeContenido(html) {
  // Un manejador en un atributo (onload=…) no se permite por hash sin 'unsafe-hashes': mejor que falle la compilación
  const manejador = html.match(/<[a-z][^>]*\son[a-z]+\s*=/i);
  if (manejador) {
    throw new Error(`index.html tiene un manejador en un atributo, que la política bloquea: ${manejador[0]}`);
  }
  const hashes = [...html.matchAll(/<script(?![^>]*\ssrc=)[^>]*>([\s\S]*?)<\/script>/gi)].map(
    ([, codigo]) => `'sha256-${createHash('sha256').update(codigo, 'utf8').digest('base64')}'`,
  );
  return [
    "default-src 'self'",
    // 'unsafe-eval': los decodificadores del visor 3D (HU-16 RF-10) son WebAssembly, y el de KTX2 además arma sus
    // funciones con new Function; sin eso, un modelo con texturas KTX2 no se ve. Un script inyectado igual queda
    // bloqueado: lo que se permite es evaluar texto desde un script que ya es de la tienda
    `script-src 'self' ${[...hashes, "'unsafe-eval'"].join(' ')}`,
    // Angular pone los estilos de cada componente en <style> y en atributos style: con hash o nonce no alcanza
    "style-src 'self' 'unsafe-inline'",
    // blob: y data: son las texturas que three saca de un .glb
    "img-src 'self' data: blob:",
    "font-src 'self'",
    "connect-src 'self' data: blob:",
    // Draco y KTX2 decodifican en workers que three arma con un blob:
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ].join('; ');
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const [archivo] = process.argv.slice(2);
  if (!archivo) {
    throw new Error('Falta la ruta del index.html compilado');
  }
  // always: también si la respuesta es un error
  process.stdout.write(
    `add_header Content-Security-Policy "${politicaDeContenido(readFileSync(archivo, 'utf8'))}" always;\n`,
  );
}
