# Pruebas de extremo a extremo del camino principal

Lo acordado en `decisiones.md` → «Decisiones técnicas del frontend»
(14/10/2026): tras la revisión total, unas 10 a 15 pruebas del camino
principal en el CI, con `@playwright/test` (instalación aprobada por Fabio).

## Tareas

1. **Entorno** (`frontend/e2e/entorno.mjs`): `levantar` genera
   `frontend/e2e/.env.e2e` con claves al azar (ignorado por git) y levanta
   `docker-compose.yml` desde cero como proyecto `tienda-e2e` en el puerto
   8090, con los simuladores de pago y de correo y el catálogo de
   `backend/src/test/resources/demo` (3 productos, uno agotado); espera a que
   la API responda con los 3. `bajar` borra contenedores y volúmenes.
   - *Decisión:* contra Docker y no contra un backend sobre H2, para probar lo
     que se publica (nginx, la API, MySQL).
   - *Decisión:* desde cero en cada corrida: el stock inicial es conocido y
     `LimiteRegistros` deja 5 cuentas por hora desde una conexión.
2. **Apoyo** (`e2e/apoyo.ts`): `registrar`, `iniciarSesion`, `clienteNuevo`,
   `administrador` (lee `.env.e2e`) y `enlaceDeVerificacion` (lee el token del
   log del simulador de correo).
3. **Pruebas** (16):
   - `catalogo.spec.ts`, sin sesión: el catálogo con el agotado; la búsqueda
     del encabezado; el producto con su precio y, al agregarlo, a iniciar
     sesión con `destino` (HU-09, HU-10, HU-11 RF-6); la contraseña
     equivocada.
   - `compra.spec.ts`, en serie con una sola página: registro (HU-01);
     agregar dos productos (HU-11); cambiar la cantidad y quitar en el
     carrito; sin verificar no confirma (HU-20 RF-6, RF-7); verificar con el
     enlace (HU-20 RF-3); confirmar con el plazo de 30 minutos (HU-12); pagar
     con el simulador (HU-13); «Mis pedidos» (HU-14); el Administrador
     registra la entrega (HU-17); el Cliente la ve.
   - `celular.spec.ts` (Pixel 7): catálogo y producto sin scroll horizontal;
     registro y carrito desde el celular.
4. **Configuración:** `playwright.config.ts` (Edge en la PC, Chromium en el
   CI, un solo worker, sin reintentos); `npm run e2e` y `npm run e2e:bajar`;
   `.gitignore` y `.dockerignore`.
5. **CI:** el trabajo «Extremo a extremo (Playwright)» reemplaza a «Imágenes
   Docker», porque construye las dos imágenes; con un fallo sube el informe y
   muestra el log de la API. No es obligatorio en el ruleset (lo decide Fabio).
6. **Docs:** `decisiones.md` (28/10/2026) y README → «Pruebas».

## Resultado

- 16 pruebas en verde en unos 40 s (más el minuto que tarda en levantar), en
  cinco corridas desde cero; frontend: 74 archivos y 874 pruebas,
  build sin avisos.
- Dos rondas de `/code-review` medium. Corregido: el `.env.e2e` se regenera
  en cada corrida y siempre se baja el volumen anterior; `bajar` sin el
  archivo; sin el script `e2e:levantar` ni reintentos; el correo escapado
  entero en la expresión, el log leído desde las últimas 500 líneas y sin
  depender de la «ó». Descartado: el check obligatorio (el ruleset solo exige
  Backend y Frontend); la traza de la página compartida (Playwright ya la
  guarda: `tracing.start` falla porque ya empezó); las constantes repetidas
  entre `entorno.mjs` (ESM, ejecuta su orden al importarse) y el TS; levantar
  en `globalSetup` (cada `playwright test` tardaría un minuto más); el caché
  de Docker en el CI, hasta ver cuánto tarda.
