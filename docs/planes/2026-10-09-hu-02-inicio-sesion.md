# Plan — HU-02 Inicio de sesión

Historia: `docs/historias/HU-02.md` · Issue #4 · Rama `feature/hu-02-inicio-sesion`

El inicio de sesión ya existe y su backend no cambia. Este plan hace tres
cambios en el frontend (aviso de sesión cerrada, «Iniciar sesión» con sesión
lleva al inicio, mostrar u ocultar la contraseña) y pasa las pruebas
existentes a citar «HU-02 RF-N».

## Tarea 1 — Aviso de sesión cerrada y vuelta a donde estaba (RF-10) · frontend

**Archivos:** `core/sesion/sesion.service.ts`, `core/http/errores.interceptor.ts`,
`features/inicio-sesion/inicio-sesion.page.ts`.

- `SesionService.vencer(): void` — cierra la sesión (`cerrar()`) y navega a
  `/inicio-sesion` con `queryParams: { aviso: 'sesion-cerrada', destino:
  router.url }`. Si la URL actual ya es `/inicio-sesion`, sin destino. La usan
  el temporizador del vencimiento (en lugar de `cerrar()`) y el interceptor
  ante un 401 de una ruta protegida (en lugar de `cerrar()` + `navigate`).
- `cerrar()` sigue igual: «Cerrar sesión» a pedido no muestra aviso (RF-11).
- `AVISOS` de la página suma `'sesion-cerrada': 'Tu sesión se cerró. Inicia
  sesión de nuevo.'`. El destino ya se valida con `destinoInterno`.

**Pruebas:**
- `sesion.service.spec.ts` → «al vencer el token lleva a iniciar sesión con el
  aviso y el destino» — HU-02 RF-10 (reemplaza a «cierra la sesión sola cuando
  el token vence»)
- `errores.interceptor.spec.ts` → «ante un 401 cierra la sesión y lleva a
  iniciar sesión con el aviso y el destino» — HU-02 RF-10
- `errores.interceptor.spec.ts` → «ante un 500 no cierra la sesión» — HU-02
  RF-10 (caso límite: un error del servidor no es sesión cerrada)
- `inicio-sesion.page.spec.ts` → «avisa que la sesión se cerró» y «después del
  aviso de sesión cerrada vuelve al destino» — HU-02 RF-10
- `app.component.spec.ts` → ««Cerrar sesión» la cierra» sigue sin aviso —
  HU-02 RF-11

## Tarea 2 — «Iniciar sesión» con sesión lleva al inicio (RF-12) · frontend

**Archivos:** `app.routes.ts`.

Se agrega `canActivate: [visitanteGuard]` a la ruta `inicio-sesion` (el guard
ya existe, HU-01). `vencer()` cierra la sesión antes de navegar, así que el
guard deja pasar.

**Pruebas** (`app.routes.spec.ts`):
- «inicio-sesion y registro usan visitanteGuard» — HU-02 RF-12, HU-01 RF-14

## Tarea 3 — Mostrar u ocultar la contraseña (RF-13) · frontend

**Archivos:** `features/inicio-sesion/inicio-sesion.page.{ts,html,scss}`.

Una señal `contrasenaVisible`, un botón `type="button"` dentro del campo con
icono de Lucide (`LucideEye` / `LucideEyeOff`, `aria-hidden`), `aria-label`
«Mostrar contraseña» / «Ocultar contraseña» y `aria-pressed`. El `input` pasa a
`[type]="contrasenaVisible() ? 'text' : 'password'"`. Área tocable de 44 px.
Queda en la página: si después se suma a registro y restablecer, se lleva a
`shared/` (YAGNI).

**Pruebas** (`inicio-sesion.page.spec.ts`):
- «la contraseña empieza oculta» — HU-02 RF-13
- «mostrar contraseña la deja ver y ofrece ocultarla» — HU-02 RF-13
- «mostrar contraseña no envía el formulario» — HU-02 RF-13

## Tarea 4 — Las pruebas existentes citan HU-02

Se cambia solo el comentario, no la prueba:

| RF | Pruebas que ya existen |
|---|---|
| RF-1 | `AutenticacionServiceTest.credencialesCorrectasDeUnaCuentaActivaEntreganUnTokenDelUsuario`, `administradorIniciaSesionIgualQueUnCliente`; `AuthControllerTest.inicioDeSesionValidoSinSesionDevuelveTokenYDatosSinContrasena`; `InicioSesionIntegracionTest.clienteRegistradoIniciaSesionYUsaElToken…`, `administradorInicialIniciaSesion…`; `TokenJwtTest.tokenEmitidoIdentificaAlUsuarioYVenceEnOchoHoras`; front: «con credenciales correctas inicia la sesión y entra», «vuelve al destino que la persona quería», «conserva los parámetros del destino», «ignora un destino que apunte fuera del sitio», servicio «envía el correo y la contraseña, y devuelve la sesión» |
| RF-2 | `AuthControllerTest.correoLlegaNormalizadoAlServicioDeAutenticacion`; front: «normaliza el correo antes de enviarlo», «no toca la contraseña…» |
| RF-3 | `AutenticacionServiceTest.contrasenaIncorrectaSeInforma…`, `correoInexistenteSeInformaIgualYTambienComparaUnaContrasena`; `InicioSesionIntegracionTest.contrasenaIncorrectaDevuelveCredencialesIncorrectas`; front: «muestra el mensaje de credenciales incorrectas y no entra» |
| RF-4 | `AutenticacionServiceTest.cuentaDesactivadaConContrasenaCorrecta…`, `cuentaDesactivadaConContrasenaIncorrecta…`, `cuentaDesactivadaConContrasenaCorrectaNoSumaIntentos`; front: «muestra el mensaje propio de la cuenta desactivada» |
| RF-5 | `AutenticacionServiceTest.cincoIntentosFallidosBloquean…`, `correoInexistenteTambienSumaIntentos`, `peticionesSimultaneasNoComparanMasDeCincoContrasenas`; `LimiteIntentosTest` (todas); `InicioSesionIntegracionTest.cincoIntentosFallidosBloqueanElCorreoSoloDesdeEsaIp`; front: «muestra cuánto hay que esperar…», servicio «un 429 del límite de intentos conserva su código» |
| RF-6 | `AutenticacionServiceTest.inicioDeSesionExitosoReiniciaElConteo`, `LimiteIntentosTest.inicioDeSesionExitosoReiniciaElConteo` |
| RF-7 | `AutenticacionServiceTest.contrasenaDeMasDe72BytesNoEntraAunqueBcryptLaTruncaria` |
| RF-8 | front: «con los campos vacíos no llama a la API y marca los dos campos», «avisa cuando el correo no tiene formato de correo» |
| RF-9 | `SeguridadJwtTest` (todas las de token y cuenta), `TokenJwtTest` (vencido, otra clave, alterado, sin huella, cambio de contraseña), `InicioSesionIntegracionTest.cuentaDesactivadaNoIniciaSesionYSuTokenAnteriorDejaDeValer`, `cambioDeContrasenaInvalidaElTokenAnterior`, `tokenInvalidoEnElHeaderNoImpideIniciarSesion`; front: `token.interceptor.spec.ts` |
| RF-10 | front: `errores.interceptor.spec.ts` «ante un 401 al iniciar sesión no navega», «ante un 403 no cierra la sesión» (más las nuevas de la Tarea 1) |
| RF-11 | front: `sesion.service.spec.ts` «cerrar limpia la sesión y el almacenamiento», `app.component.spec.ts` ««Cerrar sesión» la cierra» |

Las citas de otras historias que viven en esos archivos (cambio de contraseña
→ HU-04) se corrigen cuando se escriba esa historia.

## Decisiones

- `vencer()` en `SesionService` y no en el interceptor: el vencimiento por
  temporizador y el 401 son la misma situación y tienen que verse igual.
  `SesionService` pasa a inyectar `Router` (está en `core/`, no cruza capas).
- El botón de la contraseña queda en la página hasta que otra pantalla lo
  necesite.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: le tocan las tres (toca
sesión y tiene frontend; `/security-review` sobre el manejo del destino y del
cierre de sesión).

## Resultado

*09/10/2026, `/sdd:cerrar HU-02`.*

### Cobertura RF → pruebas

Las de la tabla de la Tarea 4 (citan «HU-02 RF-N»), más las nuevas:

| RF | Pruebas nuevas |
|---|---|
| RF-10 | `sesion.service.spec.ts`: «al vencer el token lleva a iniciar sesión con el aviso y el destino», «vencer estando en iniciar sesión no guarda destino», «vencer sin sesión no hace nada»; `errores.interceptor.spec.ts`: «ante un 401 … con el aviso y el destino», «ante un 500 no cierra la sesión»; `inicio-sesion.page.spec.ts`: «avisa que la sesión se cerró», «después del aviso de sesión cerrada vuelve al destino» |
| RF-12 | `app.routes.spec.ts`: «con sesión, iniciar sesión lleva al inicio», «sin sesión, iniciar sesión se muestra»; `restablecer.page.spec.ts`: «al restablecer cierra la sesión abierta en este navegador» |
| RF-13 | `inicio-sesion.page.spec.ts`: «la contraseña empieza oculta», «mostrar contraseña la deja ver y ofrece ocultarla», «mostrar contraseña no envía el formulario» |

Cada RF de RF-1 a RF-13 tiene al menos una prueba que lo cita.

### Suites

- Backend `./mvnw test`: **265 pruebas**, 0 fallas (sin cambios de código).
- Frontend `npx ng test --watch=false`: **371 pruebas** (antes 359); `npx ng build` sin avisos.

### Revisiones

- **`/security-review`:** sin hallazgos. El backend no cambia; `vencer()` borra el
  token antes de navegar y el destino pasa por `destinoInterno`.
- **`/code-review`:** 7 hallazgos.
  - Corregidos: restablecer con una sesión abierta perdía el aviso
    «contraseña actualizada» por el guard nuevo (ahora cierra la sesión local);
    dos 401 seguidos navegaban dos veces y perdían el destino (`vencer()` sin
    sesión no hace nada); el destino se arma con `parametrosDelDestino`;
    `useRealTimers` en `afterEach`.
  - Descartados: un 401 en medio de una navegación guarda la pantalla anterior
    como destino (raro; la persona vuelve un paso atrás); el vencimiento con
    sesión mientras se restablece la contraseña lleva a iniciar sesión (raro);
    «Cerrar sesión» y `vencer()` navegan desde lugares distintos (son casos
    distintos, con y sin aviso).
- **`web-design-guidelines`:** el botón del ojo cambiaba la etiqueta y además
  usaba `aria-pressed`; se quitó `aria-pressed`. Área de 44 px y foco visible.

### Prueba manual

09/10/2026, Fabio, con `docker compose up -d --build`: pasaron los escenarios
(entrar y volver al destino, mismo mensaje para contraseña incorrecta y correo
inexistente, ver la contraseña, «Iniciar sesión» con sesión lleva al inicio,
aviso al desactivar la cuenta con la sesión abierta, cerrar sesión sin aviso,
bloqueo tras 5 intentos). La primera pasada falló en lo nuevo porque el
contenedor del frontend no se había recompilado. Verificación cruzada de David
pendiente para el PR.

### Distinto de lo planeado

- `app.routes.spec.ts` carga la pantalla de inicio de sesión en un `beforeAll`:
  con `@lucide/angular`, compilarla la primera vez dentro de Vitest tarda ~13 s y
  pasaba el límite de 5 s de la prueba (en el build solo quedan los iconos usados).
- `restablecer.page.ts` cierra la sesión local antes de ir a «Iniciar sesión»
  (hallazgo del `/code-review`).
- Se corrigieron dos citas de HU-01 en `autenticacion.service.spec.ts` que
  habían quedado con la numeración vieja.
