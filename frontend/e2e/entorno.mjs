// La tienda completa para las pruebas de extremo a extremo: MySQL, la API y nginx de docker-compose.yml, en un
// proyecto aparte (tienda-e2e) que no toca el .env ni la base de desarrollo. Con los simuladores de pago y de correo
// y el catálogo de prueba del backend (3 productos, uno agotado).
//   node e2e/entorno.mjs levantar   la base vacía, las imágenes al día y la API lista
//   node e2e/entorno.mjs bajar      borra los contenedores y la base de las pruebas
// Las claves de .env.e2e (ignorado por git) se generan al azar en cada corrida, como la base: no son de ninguna
// cuenta real, y un archivo viejo no deja una ruta o un puerto que ya no corresponden.
import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { existsSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = resolve(aqui, '..', '..');
export const ARCHIVO_ENTORNO = join(aqui, '.env.e2e');
export const PROYECTO = 'tienda-e2e';
export const PUERTO = 8090;

const azar = (bytes) => randomBytes(bytes).toString('hex');

function crearEntorno() {
  const sitio = `http://localhost:${PUERTO}`;
  const valores = {
    TIENDA_PUERTO: PUERTO,
    TIENDA_SITIO_URL: sitio,
    TIENDA_CORS_ORIGENES: sitio,
    TIENDA_DB_PASSWORD: azar(16),
    MYSQL_ROOT_PASSWORD: azar(16),
    TIENDA_JWT_SECRETO: randomBytes(48).toString('base64'),
    TIENDA_ADMIN_CORREO: 'admin@tienda.test',
    TIENDA_ADMIN_PASSWORD: azar(12),
    TIENDA_ADMIN_NOMBRE: 'Admin',
    TIENDA_ADMIN_APELLIDO: 'Pruebas',
    TIENDA_PAGO_PROVEEDOR: 'simulador',
    TIENDA_CORREO_PROVEEDOR: 'simulador',
    TIENDA_DEMO_DIRECTORIO: join(raiz, 'backend', 'src', 'test', 'resources', 'demo'),
  };
  const lineas = Object.entries(valores).map(([nombre, valor]) => `${nombre}=${valor}`);
  writeFileSync(ARCHIVO_ENTORNO, `# Generado por e2e/entorno.mjs: solo para las pruebas\n${lineas.join('\n')}\n`);
}

function compose(...argumentos) {
  execFileSync(
    'docker',
    ['compose', '-p', PROYECTO, '-f', join(raiz, 'docker-compose.yml'), '--env-file', ARCHIVO_ENTORNO, ...argumentos],
    { stdio: 'inherit', cwd: raiz },
  );
}

// La API carga el catálogo al arrancar: lista cuando el catálogo responde con los 3 productos
async function esperarLaApi() {
  const limite = Date.now() + 5 * 60_000;
  while (Date.now() < limite) {
    try {
      const respuesta = await fetch(`http://localhost:${PUERTO}/api/productos`);
      if (respuesta.ok && (await respuesta.json()).totalElementos === 3) {
        return;
      }
    } catch {
      // nginx o la API todavía no responden
    }
    await new Promise((listo) => setTimeout(listo, 2000));
  }
  throw new Error('La API no quedó lista en 5 minutos: docker compose -p tienda-e2e logs backend');
}

const orden = process.argv[2];
if (orden === 'levantar') {
  // Cada corrida desde una base vacía: las pruebas cuentan con el stock inicial del catálogo, y MySQL no toma las
  // claves nuevas sobre un volumen viejo. down solo necesita que las variables existan, no sus valores anteriores
  crearEntorno();
  compose('down', '--volumes', '--remove-orphans');
  compose('up', '--detach', '--build');
  await esperarLaApi();
} else if (orden === 'bajar') {
  if (!existsSync(ARCHIVO_ENTORNO)) {
    crearEntorno();
  }
  compose('down', '--volumes', '--remove-orphans');
} else {
  console.error('Uso: node e2e/entorno.mjs levantar|bajar');
  process.exit(1);
}
