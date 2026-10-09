# Plan — HU-03 Recuperación de contraseña

Historia: `docs/historias/HU-03.md` · Issue #5 · Rama `feature/hu-03-recuperacion`

La recuperación ya existe. Este plan deja de enviar el enlace a las cuentas
desactivadas (y anula los enlaces pendientes de esas cuentas), lleva el correo
a los colores de la marca y pasa las pruebas existentes a citar «HU-03 RF-N».
La prueba real con Gmail va en la prueba manual.

## Tarea 1 — Cuentas desactivadas sin enlace (RF-3, RF-9) · backend

**Archivos:** `service/RecuperacionContrasenaService.java`.

- `solicitar`: el límite se cuenta igual, pero `iniciarRecuperacion` solo
  corre si `usuario.estaActiva()`. La respuesta no cambia.
- `restablecer`: si la cuenta del token está desactivada, lanza
  `TokenRecuperacionInvalidoException` (mismo mensaje que vencido o usado).

**Pruebas:**
- `RecuperacionContrasenaServiceTest.cuentaDesactivadaNoRecibeCorreoNiToken` —
  HU-03 RF-3 (reemplaza a `cuentaDesactivadaTambienRecibeElTokenYSigueDesactivada`)
- `RecuperacionContrasenaServiceTest.tokenDeUnaCuentaDesactivadaNoCambiaLaContrasena`
  — HU-03 RF-9 (reemplaza a `restablecerEnUnaCuentaDesactivadaNoLaReactiva`)
- `RecuperacionContrasenaIntegracionTest.cuentaDesactivadaNoRecibeEnlaceYSuEnlaceAnteriorNoSirve`
  — HU-03 RF-2, RF-3, RF-9 (reemplaza a `cuentaDesactivadaRecuperaLaContrasenaSinReactivarse`)

## Tarea 2 — Correo con los colores de la marca (RF-13) · backend

**Archivos:** `service/NotificadorCorreoSmtp.java`.

Los colores fijos del HTML (encabezado, botón, enlace, texto suave) pasan a la
paleta nueva: encabezado grafito `#16181b` con la marca en amarillo
`#f2b81c`, botón amarillo con texto grafito, raya amarilla bajo el
encabezado. Los correos no leen CSS externo: van como estilos en línea.

**Pruebas** (`NotificadorCorreoSmtpTest`):
- `elHtmlUsaLosColoresDeLaMarca` — HU-03 RF-13

## Tarea 3 — Las pruebas existentes citan HU-03

| RF | Pruebas que ya existen |
|---|---|
| RF-1 | `RecuperacionContrasenaServiceTest.correoRegistradoGuardaElHashDelTokenConVigenciaDeUnaHoraYLeEnviaElToken`; `RecuperacionContrasenaIntegracionTest.flujoCompletoCambiaLaContrasenaYElTokenNoSePuedeReutilizar`; `NotificadorCorreoSmtpTest.laRecuperacionLlevaElEnlaceConElTokenEnTextoYEnHtml`; front: «pide la recuperación y confirma sin decir si la cuenta existe» |
| RF-2 | `RecuperacionContrasenaIntegracionTest.correoNoRegistradoRecibeLaMismaRespuestaSinQueSeEnvieCorreo`; front: «un correo sin cuenta ve la misma confirmación, palabra por palabra» |
| RF-3 | `RecuperacionContrasenaServiceTest.correoNoRegistradoTerminaIgualSinGuardarNiEnviarNada` |
| RF-4 | front: servicio «solicita la recuperacion con el correo en minusculas y sin espacios» |
| RF-5 | `RecuperacionContrasenaIntegracionTest.laBaseGuardaElHashDelTokenYEseValorNoSirveComoToken` |
| RF-6 | `RecuperacionContrasenaServiceTest.nuevaSolicitudInvalidaLasRecuperacionesPendientesAnteriores`; `RecuperacionContrasenaIntegracionTest.segundaSolicitudInvalidaElTokenDeLaPrimera`, `dosSolicitudesSimultaneasDejanUnSoloTokenVigente` |
| RF-7 | `RecuperacionContrasenaServiceTest.cuartaSolicitudDelMismoCorreoDesdeLaMismaIp…`; `LimiteSolicitudesRecuperacionTest` (todas); front: «muestra el aviso del límite…», servicio «rechaza con SOLICITUDES_EXCEDIDAS…» |
| RF-8 | `RecuperacionContrasenaServiceTest.tokenVigenteCambiaLaContrasenaEInvalidaLasRecuperacionesPendientesDeLaCuenta`; `RecuperacionContrasenaIntegracionTest.elTokenDeUnaCuentaSoloCambiaLaContrasenaDeEsaCuenta`; front: «restablece con el token del enlace y lleva a iniciar sesión», servicio «restablece la contrasena con el token tal como vino» |
| RF-9 | `RecuperacionContrasenaServiceTest.tokenVencidoNoCambiaLaContrasena`, `tokenYaUsadoNoCambiaLaContrasena`, `tokenDesconocidoSeRechazaConElMismoError`; `RecuperacionContrasenaIntegracionTest.tokenVencidoNoCambiaLaContrasena`; front: «avisa que el enlace ya no sirve y ofrece pedir otro», «no muestra el formulario y explica qué hacer», «un token de solo espacios…», servicio «rechaza con TOKEN_RECUPERACION_INVALIDO» |
| RF-10 | front: «frena una contraseña de 7 caracteres», «frena una contraseña de más de 72 bytes», «reparte los errores del backend bajo el campo», servicio «conserva los campos a corregir de un 400» |
| RF-11 | `RecuperacionContrasenaIntegracionTest.restablecerLaContrasenaInvalidaLosTokensDeSesionAnteriores`, `despuesDeRestablecerSePuedeIniciarSesionAunqueElCorreoEstuvieraBloqueado`; `RecuperacionContrasenaServiceTest.restablecerLaContrasenaQuitaElBloqueoDeInicioDeSesionDeLaCuenta` |
| RF-12 | front: «no escribe el token en la pantalla» |
| RF-13 | `NotificadorCorreoSmtpTest.laRecuperacionVaAlUsuarioDesdeElRemitenteDelSitio`, `elCorreoVaFirmadoConLaMarcaDelSitio`, `elNombreDelUsuarioSeEscapaEnElHtml`; prueba manual con Gmail |

## Decisiones

- El límite de solicitudes cuenta también para las cuentas desactivadas: así
  la respuesta y el conteo son iguales para todos los correos.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: le tocan las tres (toca
contraseñas y es un endpoint público que dispara correos; tiene frontend).

## Resultado

*09/10/2026, `/sdd:cerrar HU-03`.*

### Cobertura RF → pruebas

Las de la tabla de la Tarea 3 (citan «HU-03 RF-N»), más las nuevas:

| RF | Pruebas nuevas |
|---|---|
| RF-2, RF-3 | `RecuperacionContrasenaServiceTest.cuentaDesactivadaNoRecibeCorreoNiToken` |
| RF-9 | `GestionUsuariosServiceTest.desactivarAnulaLosEnlacesDeRecuperacionPendientes`, `reactivarNoTocaLosEnlacesDeRecuperacion`; `RecuperacionContrasenaIntegracionTest.cuentaDesactivadaNoRecibeEnlaceYSuEnlaceAnteriorNoSirveNiAlReactivarla` |
| RF-13 | `NotificadorCorreoSmtpTest.elHtmlUsaLosColoresDeLaMarca` |

Cada RF de RF-1 a RF-13 tiene al menos una prueba que lo cita.

### Suites

- Backend `./mvnw test`: **267 pruebas**, 0 fallas (antes 265).
- Frontend `npx ng test --watch=false`: **371 pruebas**; `npx ng build` sin avisos.

### Revisiones

- **`/security-review`:** sin hallazgos. Las cuentas desactivadas quedan sin
  enlace y la respuesta es la misma para todos los correos. La diferencia de
  milisegundos entre una cuenta activa (guarda el token) y un correo sin cuenta
  ya existía; la cuenta desactivada ahora se comporta como un correo sin cuenta.
- **`/code-review`:** 7 hallazgos.
  - Corregidos: el enlace de una cuenta desactivada revivía si se la
    reactivaba (se filtraba al usarlo): ahora `GestionUsuariosService` anula los
    enlaces pendientes **al desactivar**, y el filtro de `restablecer` se quitó;
    la prueba de integración desactiva y reactiva por el servicio real;
    sangría rota en `restablecer.page.spec.ts`; comentarios partidos en
    `recuperacion.page.spec.ts`.
  - Descartados: carrera entre restablecer y desactivar al mismo tiempo
    (milisegundos; aunque pase, la cuenta sigue desactivada y no entra); los
    colores repetidos en la prueba del correo (es lo que la prueba verifica).
- **`web-design-guidelines`:** sin cambios en las pantallas.

### Prueba manual

09/10/2026, Fabio, con `docker compose up -d --build` y
`TIENDA_CORREO_PROVEEDOR=smtp` (cuenta de Gmail del proyecto con contraseña de
aplicación): **el correo real llegó** con los colores de la marca, el enlace
cambió la contraseña y no sirvió por segunda vez; correo inexistente, token
inválido y contraseña corta respondieron como dice la historia (también
probados por la API). Verificación cruzada de David pendiente para el PR.

### Distinto de lo planeado

- La regla de la cuenta desactivada se aplica al desactivar
  (`GestionUsuariosService`) y no al usar el enlace (hallazgo del
  `/code-review`).
- El primer arranque con Gmail falló porque `TIENDA_CORREO_PROVEEDOR` tenía un
  correo en lugar de `smtp`: el backend no arranca y el error de Spring no lo
  dice claro. Queda como mejora posible (validar el valor al arrancar).
