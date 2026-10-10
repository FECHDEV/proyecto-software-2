# Plan — HU-05 Gestión de usuarios

Historia: `docs/historias/HU-05.md` · Issue #7 · Rama `feature/hu-05-gestion-usuarios`

La gestión ya existe y el rol Contador ya está completo. Este plan hace que
desactivar invalide las sesiones para siempre (versión de sesión en la cuenta y
en el token) y pasa las pruebas existentes a citar «HU-05 RF-N».

## Tarea 1 — Versión de sesión (RF-5, RF-6) · backend

**Archivos:** `entity/Usuario.java`, `config/TokenJwt.java`,
`config/TokenLeido.java`.

- `Usuario.versionSesion` (`int`, columna `version_sesion`, no nula,
  `@ColumnDefault("0")` para las filas que ya existen; sin migraciones, el
  esquema lo actualiza Hibernate). `desactivar()` la incrementa si la cuenta
  estaba activa; `activar()` no la toca.
- `TokenJwt.emitir` suma el claim `vs` con la versión. `leer` exige `vs`
  (un token sin `vs`, emitido antes de este cambio, no vale: hay que iniciar
  sesión otra vez, una sola vez). `TokenLeido(idUsuario, huellaContrasena,
  versionSesion)`.
- `TokenJwt.correspondeA` compara la huella **y** la versión.
  `JwtFiltroAutenticacion` no cambia: ya usa `correspondeA`.

Es seguridad (tokens), así que el código va completo:

```java
// Usuario
@ColumnDefault("0")
@Column(name = "version_sesion", nullable = false)
private int versionSesion;

public void desactivar() {
	if (estado == EstadoCuenta.ACTIVA) {
		versionSesion++;
	}
	estado = EstadoCuenta.DESACTIVADA;
}

// TokenJwt
private static final String VERSION_SESION = "vs";
// emitir: .claim(VERSION_SESION, usuario.getVersionSesion())
// leer:
Integer version = claims.get(VERSION_SESION, Integer.class);
if (huella == null || version == null) {
	return Optional.empty();
}
return Optional.of(new TokenLeido(Long.valueOf(claims.getSubject()), huella, version));
// correspondeA:
return leido.versionSesion() == usuario.getVersionSesion()
		&& MessageDigest.isEqual(…huellas…);
```

**Pruebas:**
- `UsuarioTest.desactivarIncrementaLaVersionDeSesionYReactivarNoLaVuelveAtras` — HU-05 RF-5, RF-6
- `UsuarioTest.desactivarUnaCuentaYaDesactivadaNoCambiaLaVersion` — HU-05 RF-5
- `TokenJwtTest.tokenLlevaLaVersionDeSesionDeLaCuenta` — HU-05 RF-5
- `TokenJwtTest.tokenDeUnaVersionAnteriorNoCorrespondeALaCuenta` — HU-05 RF-6
- `TokenJwtTest.tokenSinVersionDeSesionNoSeAcepta` — HU-05 RF-6
- `GestionUsuariosIntegracionTest.tokenEmitidoAntesDeDesactivarNoValeDespuesDeReactivar` (401 con el token viejo, 200 con uno nuevo) — HU-05 RF-5, RF-6

## Tarea 2 — Las pruebas existentes citan HU-05

| RF | Pruebas que ya existen |
|---|---|
| RF-1 | `UsuariosControllerTest` (sin sesión 401, otros roles 403); `app.routes.spec.ts` «la gestión de usuarios es solo del administrador», «sin sesión…»; `app.component.spec.ts` (menú) |
| RF-2 | `GestionUsuariosServiceTest.listarPideLaPaginaIndicadaDelRegistroMasRecienteAlMasAntiguo`; `UsuarioRepositoryTest.buscarSinFiltros…`; `UsuariosControllerTest.listadoPasaLosFiltrosYDevuelveLaPaginaSinDatosSensibles`; front: «muestra los usuarios de la página», «informa la página y el total», «pasa a la página siguiente…», «en la primera página no se puede ir atrás…», «descarta una página demasiado grande», «fuera de rango ofrece volver a la primera página», «mientras carga lo avisa», «si la lista no carga deja reintentar» |
| RF-3 | `UsuarioRepositoryTest.buscarCombinaRolEstadoYTextoEnCorreoNombreYApellido`; front: los de filtros, búsqueda y URL, «sin resultados lo dice…» |
| RF-4 | `GestionUsuariosServiceTest` (cambio de rol); `GestionUsuariosIntegracionTest`; `SeguridadJwtTest.rolSeTomaDeLaCuentaYNoDelToken`; front: «cambiar el rol pide confirmación…», «al confirmar cambia el rol…», «al cancelar no cambia nada…» |
| RF-5 | `GestionUsuariosServiceTest.desactivaLaCuentaDeOtroUsuarioSinCambiarSuRol`, `desactivarAnulaLosEnlacesDeRecuperacionPendientes`; front: «desactivar pide confirmación…», «cancelar la desactivación…» |
| RF-6 | `GestionUsuariosServiceTest.activarLaPropiaCuentaNoTieneReglaExtra`, `reactivarNoTocaLosEnlacesDeRecuperacion`; front: «reactivar no pide confirmación» |
| RF-7 | `GestionUsuariosServiceTest` (rol propio); front: «la fila propia no ofrece acciones» |
| RF-8 | `GestionUsuariosServiceTest.desactivarLaPropiaCuentaSeRechazaSinTocarLaBase` |
| RF-9 | `GestionUsuariosServiceTest.desactivarAlUltimoAdministradorActivoSeRechaza` y las de quitar el rol; `GestionUsuariosIntegracionTest.dosAdministradoresQueSeQuitanElRolAlMismoTiempo…`; `UsuarioRepositoryTest.administradoresActivosConBloqueo…`, `laTablaUsuarioTieneUnIndicePorRolYEstado` |
| RF-10 | `GestionUsuariosServiceTest` (rol de cuenta desactivada); front: «en una cuenta desactivada no se puede cambiar el rol» |
| RF-11 | `GestionUsuariosIntegracionTest.operacionesSobreLaPropiaCuentaYUsuarioInexistenteSeRechazan`; `GestionUsuariosServiceTest` (usuario inexistente, operación simultánea) |
| RF-12 | front: «si el backend rechaza el cambio avisa el motivo y no toca la fila», «mientras se guarda, los controles de la fila quedan deshabilitados», «cada fila queda deshabilitada hasta que termina su propia operación», «explica un acceso denegado con el diccionario» |
| RF-13 | front: «los controles nombran a la persona de la fila» (selector con los cuatro roles); `UsuarioRepositoryTest.rolYEstadoSeGuardan…` |

Al aplicarla se revisan los nombres exactos de las pruebas de
`GestionUsuariosServiceTest` que esta tabla describe por tema.

## Decisiones

- La versión de sesión sube solo al desactivar: cambiar el rol no necesita
  cortar sesiones, porque el rol ya se lee de la base en cada petición.
- Un token sin `vs` no vale: al desplegar este cambio, cada persona inicia
  sesión una vez más. Es más simple y más seguro que aceptar tokens viejos.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: las tres (toca tokens y
roles; tiene frontend, aunque solo cambian comentarios de pruebas).

## Resultado

*10/10/2026, `/sdd:cerrar HU-05`.*

### Cobertura RF → pruebas

Las de la tabla de la Tarea 2 (citan «HU-05 RF-N»), más las nuevas:

| RF | Pruebas nuevas |
|---|---|
| RF-5, RF-6 | `UsuarioTest.desactivarIncrementaLaVersionDeSesionYReactivarNoLaVuelveAtras`, `desactivarUnaCuentaYaDesactivadaNoCambiaLaVersion`; `TokenJwtTest.tokenLlevaLaVersionDeSesionDeLaCuenta`, `tokenDeUnaVersionAnteriorNoCorrespondeALaCuenta`, `tokenSinVersionDeSesionNoSeAcepta`; `GestionUsuariosIntegracionTest.tokenEmitidoAntesDeDesactivarNoValeDespuesDeReactivar` |
| RF-13 | `usuarios.page.spec.ts`: «el selector de cada fila ofrece los cuatro roles fijos» |

Cada RF de RF-1 a RF-13 tiene al menos una prueba que lo cita.

### Suites

- Backend `./mvnw test`: **273 pruebas**, 0 fallas (antes 267).
- Frontend `npx ng test --watch=false`: **380 pruebas** (antes 379); `npx ng build` sin avisos.

### Revisiones

- **`/security-review`:** sin hallazgos. La versión viaja en el token firmado;
  un token sin `vs` no se acepta; un inicio de sesión simultáneo a una
  desactivación emite un token con la versión vieja, que queda rechazado.
- **`/code-review`:** 7 hallazgos.
  - Corregidos: `decisiones.md` → «Autenticación por JWT stateless» no
    mencionaba `vs`; la consecuencia del despliegue (todos inician sesión una
    vez más) quedó anotada como aceptada; dos pruebas citaban RF-13 sin probarlo
    (retaggeadas a RF-4/RF-5) y se agregó la de los cuatro roles.
  - Descartados: falta de prueba de punta a punta de los enlaces de
    recuperación al reactivar (la tiene `RecuperacionContrasenaIntegracionTest`,
    HU-03); unificar la huella y la versión en un solo contador (cambio grande
    sobre algo probado); que otro camino cambie el estado sin `desactivar()` (el
    campo es privado y no hay otro).
- **`web-design-guidelines`:** sin cambios en las pantallas.

### Prueba manual

10/10/2026, Fabio, con Docker: pasaron los escenarios. El primer intento del
punto 5 falló porque el contenedor del backend era del día anterior (Docker
Desktop estaba cerrado y no se recompiló); recompilado, la sesión abierta antes
de desactivar y reactivar quedó cerrada con el aviso, y una sesión nueva
funcionó. Verificación cruzada de David pendiente para el PR.

### Distinto de lo planeado

- La prueba de los cuatro roles (RF-13) se agregó por el `/code-review`.
