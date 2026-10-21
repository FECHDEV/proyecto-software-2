# Plan — HU-20 Verificar el correo para comprar

Historia: `docs/historias/HU-20.md` · Issue #32 · Rama `feature/hu-20-verificar-correo`

Backend: `Usuario` con el correo verificado o no, la tabla de enlaces
`verificacion_correo` (con el esquema de la recuperación), el correo nuevo, el
reenvío con su límite y el rechazo al confirmar. Frontend: la página
`/verificacion`, el aviso en el carrito y en la confirmación, y el estado en
«Mis datos».

## Tarea 1 — La cuenta, el enlace y el correo · backend

**Archivos:** `entity/Usuario.java`, `entity/VerificacionCorreo.java`,
`repository/VerificacionCorreoRepository.java`,
`service/VerificacionCorreoService.java`,
`service/LimiteReenviosVerificacion.java`, `service/NotificadorCorreo.java`
(y sus dos implementaciones), `service/UsuarioService.java`,
`controller/AuthController.java`, `controller/CuentaController.java`,
`config/SecurityConfig.java`, `dto/UsuarioResponse.java`,
`dto/VerificacionCorreoRequest.java`; excepciones
`TokenVerificacionInvalidoException` (400 `TOKEN_VERIFICACION_INVALIDO`),
`ReenviosExcedidosException` (429 `REENVIOS_EXCEDIDOS`) y
`CorreoYaVerificadoException` (409 `CORREO_YA_VERIFICADO`).

- **`Usuario.correoVerificado`**, con el mismo truco que `versionSesion` para
  las filas que ya existen (RF-2): Hibernate agrega la columna con el valor
  por defecto, así las cuentas de antes quedan verificadas sin migración.

```java
// Las cuentas que ya existían cuando se agregó la columna quedan verificadas (HU-20 RF-2); solo el registro la crea
// en false
@ColumnDefault("true")
@Column(name = "correo_verificado", nullable = false)
private boolean correoVerificado;
```

  `registrarCliente` la pone en `false`, `crearAdministrador` en `true`;
  `marcarCorreoVerificado()`.
- **`VerificacionCorreo`**: copia de `RecuperacionContrasena` (hash del
  token, creación, vencimiento, usado) con `VIGENCIA = 24 h`. El token sale de
  `TokenRecuperacion.generar()` y `hash()`, sin cambiarle el nombre (ver
  «Decisiones del plan»). Repositorio con `findByHashToken` bloqueado,
  `findByUsuarioAndUsadoFalse`, como el de la recuperación, y
  `idUsuarioDelToken(hash)`: una proyección sin bloqueo que no carga la
  entidad (ver «Lo delicado»).
- `VerificacionCorreoRequest(token)` con las mismas validaciones del token de
  `RestablecimientoContrasenaRequest`.
- **`LimiteReenviosVerificacion extends LimiteDeFrecuencia`**: 3 en 15
  minutos, bloqueo de 15. Es por cuenta (para reenviar hay sesión): se llama
  con el correo de la cuenta y una IP fija, `""`, con un comentario en la
  clase que lo explica.
- **`NotificadorCorreo.enviarVerificacion(correo, nombre, token)`**: asunto
  «Confirme su correo», enlace `urlDelSitio + "/verificacion?token=" +
  token`, texto plano y HTML con el nombre escapado, `@Async` en el adaptador
  SMTP (RF-13, RF-14); el simulador lo escribe en el log como la
  recuperación.
- **API:**
  - `POST /api/auth/verificacion` `{token}` (pública, en `SecurityConfig`)
    → 200 `MensajeResponse` «Su correo quedó verificado.»; 400
    `TOKEN_VERIFICACION_INVALIDO`.
  - `POST /api/cuenta/verificacion` (con sesión; la cuenta sale del token)
    → 202 `MensajeResponse` «Le enviamos el correo a …»; 409
    `CORREO_YA_VERIFICADO`; 429 `REENVIOS_EXCEDIDOS`.
  - `UsuarioResponse` suma `correoVerificado` (lo lee «Mis datos»).
- **El registro** llama a `verificacionCorreo.iniciar(usuario)` después de
  guardar la cuenta, dentro de la misma transacción (RF-1). Si el registro
  falla, no se envía nada; si falla el envío, el registro responde igual
  (`@Async`).

**Lo delicado: el token y la cuenta** (RF-3 a RF-5, RF-9, RF-11, RF-12).
Todo lo que escribe la cuenta la bloquea antes: `Usuario` no tiene
`@DynamicUpdate` y un guardado sin bloqueo pisaría un cambio de datos
simultáneo.

```java
@Transactional
public void iniciar(Usuario usuario) { // la cuenta ya está bloqueada o es nueva
	if (!usuario.estaActiva() || usuario.isCorreoVerificado()) { // RF-11, RF-12
		return;
	}
	invalidarPendientes(usuario);
	String token = TokenRecuperacion.generar();
	verificacionRepository.save(VerificacionCorreo.crear(usuario, TokenRecuperacion.hash(token), LocalDateTime.now(clock)));
	notificadorCorreo.enviarVerificacion(usuario.getCorreo(), usuario.getNombre(), token);
}

// Con la cuenta bloqueada, dos reenvíos a la vez se esperan y queda un solo enlace vigente (RF-9)
@Transactional
public void reenviar(Long idUsuario) {
	Usuario usuario = usuarioRepository.findConBloqueoByIdUsuario(idUsuario).orElseThrow();
	if (usuario.isCorreoVerificado()) {
		throw new CorreoYaVerificadoException(); // RF-11: no cuenta para el límite ni envía nada
	}
	limiteReenvios.registrarIntento(usuario.getCorreo(), ""); // RF-10
	iniciar(usuario);
}

// Cuenta → enlace, como reenviar: el id de la cuenta se lee sin bloqueo y sin cargar el enlace, que se trae recién
// con la cuenta bloqueada (una entidad ya leída no se vuelve a leer al bloquearla)
@Transactional
public void verificar(String token) {
	String hash = TokenRecuperacion.hash(token);
	Long idUsuario = verificacionRepository.idUsuarioDelToken(hash).orElseThrow(TokenVerificacionInvalidoException::new);
	Usuario usuario = usuarioRepository.findConBloqueoByIdUsuario(idUsuario)
			.filter(Usuario::estaActiva) // RF-5: el enlace de una cuenta desactivada no sirve
			.orElseThrow(TokenVerificacionInvalidoException::new);
	LocalDateTime ahora = LocalDateTime.now(clock);
	verificacionRepository.findByHashToken(hash)
			.filter(registro -> registro.estaVigente(ahora))
			.orElseThrow(TokenVerificacionInvalidoException::new);
	usuario.marcarCorreoVerificado();
	invalidarPendientes(usuario); // incluye el que se acaba de usar (RF-3)
}
```

**Orden de los bloqueos: siempre la cuenta y después sus enlaces.** `reenviar`
bloquea la cuenta y marca los enlaces usados (el `UPDATE` bloquea cada fila);
`verificar` hace lo mismo. Si `verificar` bloqueara primero el enlace, un
reenvío y una verificación a la vez se trabarían (*deadlock*) y uno
respondería 500. El enlace se vuelve a pedir con bloqueo después de la
cuenta: dos verificaciones del mismo enlace se esperan y la segunda lo
encuentra usado.

**Pruebas** (`VerificacionCorreoIntegracionTest`, con el notificador espía y el
reloj de prueba):
- `alRegistrarseQuedaSinVerificarYRecibeElEnlace` (con sesión iniciada) —
  HU-20 RF-1
- `elEnlaceVerificaLaCuentaYSirveUnaSolaVez` — HU-20 RF-3, RF-4
- `laBaseGuardaElHashYNoElToken` — HU-20 RF-3
- `elTokenDeUnaCuentaNoVerificaOtra` — HU-20 RF-4
- `verificaSinSesionYNoDevuelveToken` — HU-20 RF-4
- `tokenVencidoUsadoInexistenteODeCuentaDesactivadaDanElMismoError` (24 h
  menos un segundo sirve; 24 h no) — HU-20 RF-3, RF-5
- `reenviarInvalidaElEnlaceAnterior` — HU-20 RF-9
- `dosReenviosALaVezDejanUnSoloEnlaceVigente` — HU-20 RF-9
- `elCuartoReenvioEn15MinutosResponde429SinEnviar` — HU-20 RF-10
- `reenviarConElCorreoVerificadoResponde409SinEnviar` — HU-20 RF-11
- `aUnaCuentaDesactivadaNoSeLeEnvia` — HU-20 RF-12
- `verificarYReenviarALaVezNoSeTraban` (dos hilos, como la prueba de
  concurrencia de los pedidos) — HU-20 RF-4, RF-9
- `NotificadorCorreoSmtpTest.unFalloDeLaVerificacionSeRegistraSinPropagarse`
  (como el de la recuperación: la garantía vive en el adaptador `@Async`) —
  HU-20 RF-13
- `datosPersonalesTraeElEstadoDelCorreo` — HU-20 RF-8
- `UsuarioRepositoryTest.unaFilaSinLaColumnaQuedaVerificada` (insertada con
  SQL sin `correo_verificado`) — HU-20 RF-2
- `AdministradorInicialTest.quedaVerificado` — HU-20 RF-2
- `NotificadorCorreoSmtpTest.verificacionConEnlaceYNombreEscapado` — HU-20
  RF-14

## Tarea 2 — El rechazo al confirmar · backend

**Archivos:** `service/PedidoService.java`, `service/CarritoService.java`,
`dto/CarritoResponse.java`; excepción `CorreoSinVerificarException` (403
`CORREO_SIN_VERIFICAR`).

- En `confirmar`, justo después de `bloquearCliente`, antes de vencer,
  reservar o leer el carrito: `if (!cliente.isCorreoVerificado()) throw new
  CorreoSinVerificarException();` (RF-6). Con la cuenta bloqueada, una
  verificación simultánea espera o ya terminó: no hay estado a medias.
- `CarritoResponse` suma `correoVerificado` (RF-7, RF-15): el carrito ya se
  pide al abrirlo, así que un correo verificado en otra pestaña se ve la
  próxima vez, sin volver a iniciar sesión.
- **Las pruebas que ya confirman pedidos** (HU-12, HU-13, HU-14) registran
  clientes por la API: su ayudante de registro marca el correo como
  verificado con el repositorio, para no repetir el enlace en cada prueba.

**Pruebas** (`PedidoIntegracionTest`):
- `sinVerificarNoConfirmaNiReservaStock` (403, stock y carrito sin cambios)
  — HU-20 RF-6
- `alVerificarYaPuedeConfirmar` — HU-20 RF-6, RF-15
- `CarritoIntegracionTest.elCarritoDiceSiElCorreoEstaVerificado` — HU-20
  RF-7, RF-15

## Tarea 3 — La verificación, el carrito y «Mis datos» · frontend

**Archivos:** `core/modelos/carrito.ts`, `core/modelos/usuario.ts`,
`core/api/autenticacion.service.ts`, `core/api/cuenta.service.ts`,
`core/api/mensajes-error.ts`, `features/verificacion/verificacion.page.ts`,
`.html`, `.scss`, `.spec.ts` (nuevos), `app.routes.ts`,
`features/carrito/carrito.page.*`, `features/confirmacion/confirmacion.page.*`,
`features/mis-datos/mis-datos.page.*`.

- Modelos: `correoVerificado: boolean` en `Carrito` y en `Usuario`.
- `AutenticacionService.verificarCorreo(token): Promise<void>` y
  `CuentaService.reenviarVerificacion(): Promise<string>` (el mensaje), con
  `HttpClient`.
- Mensajes de los códigos nuevos en `mensajes-error.ts`.
- **`/verificacion`** (pública, sin guard; siguiendo `restablecer`): lee
  `?token=` (recortado), lo envía una vez al abrir y muestra «Verificando…»,
  después «Su correo quedó verificado» con «Ir a la tienda», o el mensaje del
  enlace que no sirve con «Pida otro desde su carrito o Mis datos». Sin token
  no llama a la API. El token nunca se muestra (RF-16).
- **Carrito:** con `correoVerificado === false` y sin pedido pendiente, en
  lugar de «Confirmar pedido», «Verifique su correo para confirmar» y el
  botón «Reenviar el correo» (deshabilitado mientras envía; después, el
  mensaje o el error en una región `aria-live`). **Confirmación**
  (`/confirmar-pedido` abierta a mano): el mismo aviso en lugar del
  formulario, y si el backend responde `CORREO_SIN_VERIFICAR`, también.
- **Mis datos:** junto al correo, «Verificado» o «Sin verificar» con
  «Reenviar el correo»; con `CORREO_YA_VERIFICADO`, se vuelve a pedir la
  cuenta y queda «Verificado».
- El botón de reenvío va en cada página (una llamada y una señal); no se saca
  a `shared/` porque usa `core/`.

**Pruebas:**
- `verificacion.page.spec`: `verifica el token y ofrece ir a la tienda` —
  HU-20 RF-4; `un token que no sirve muestra el mismo mensaje` — HU-20 RF-5;
  `sin token no llama a la API` — HU-20 RF-5; `no muestra el token` — HU-20
  RF-16; `un doble render no envía dos veces` — caso borde.
- `carrito.page.spec`: `sin verificar muestra el aviso en lugar de Confirmar
  pedido` — HU-20 RF-7; `sin verificar puede cambiar cantidades y quitar
  líneas` — HU-20 RF-7; `Reenviar el correo avisa y se deshabilita mientras
  envía` — HU-20 RF-9; `el 429 dice que espere` — HU-20 RF-10; `verificado
  muestra Confirmar pedido` — HU-20 RF-15.
- `confirmacion.page.spec`: `sin verificar no muestra el formulario` y `el
  403 CORREO_SIN_VERIFICAR muestra el aviso` — HU-20 RF-6, RF-7.
- `mis-datos.page.spec`: `muestra el estado del correo y el reenvío` y `el
  409 deja Verificado` — HU-20 RF-8, RF-11.
- `app.routes.spec`: `verificacion es pública` — HU-20 RF-4.
- `autenticacion.service.spec` y `cuenta.service.spec`: las dos llamadas —
  HU-20 RF-4, RF-9.

## Decisiones del plan

- **Tabla propia, no la de la recuperación:** son enlaces con vigencia y
  propósito distintos; una columna «tipo» en `recuperacion_contrasena` haría
  que un enlace de verificación pudiera probarse como de recuperación.
- **`TokenRecuperacion` se reusa sin renombrarlo:** genera y resume tokens
  sin nada propio de la recuperación; renombrarlo toca HU-03 sin ganar nada.
- **409 al reenviar ya verificado** (RF-11), así la pantalla se pone al día;
  no cuenta para el límite.
- **403 al confirmar sin verificar** (RF-6): la persona está identificada
  pero le falta un paso, como un permiso.
- **El correo que se envía al registrarse no cuenta para el límite** de
  reenvíos: el límite es para el botón.

*21/10/2026 (`/sdd:analizar`).* Corregidos el orden de los bloqueos de
`verificar` (cuenta → enlace), la prueba de RF-13 (al adaptador SMTP) y la
prueba de las cantidades sin verificar. Pendiente fuera de esta historia:
`RecuperacionContrasenaService` cruza los bloqueos igual (`solicitar` cuenta →
enlace, `restablecer` enlace → cuenta); va al «Resultado».

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: interfaz (pantalla
nueva y avisos en el carrito, la confirmación y «Mis datos»), `/code-review`
y **`/security-review`** (tokens nuevos, un endpoint público y un cambio a
quién puede confirmar). La prueba real del correo con la cuenta de Gmail del
proyecto (RF-14) la hace Fabio.

## Resultado

*21/10/2026, `/sdd:cerrar HU-20`.* Las tres tareas, las revisiones y la
prueba manual del usuario, con el correo real por Gmail («funciona todo»).

### Cobertura RF → pruebas

| RF | Backend | Frontend |
|---|---|---|
| RF-1 | `alRegistrarseQuedaSinVerificarConLaSesionIniciadaYRecibeElEnlace`, `unRegistroRechazadoNoEnviaElCorreo`, `unClienteRegistradoQuedaSinVerificar`, `escribeEnElLogLaVerificacionConSuToken` | — |
| RF-2 | `unaFilaSinLaColumnaDelCorreoVerificadoQuedaVerificada`, `sinAdministradorCreaUnoActivoConLasVariables` | — |
| RF-3 | `elEnlaceVerificaLaCuentaYSirveUnaSolaVez`, `laBaseGuardaElHashDelTokenConVigenciaDe24HorasYEseValorNoSirveComoToken`, `VerificacionCorreoTest` (24 h menos un segundo, 24 h, usado) | — |
| RF-4 | `elEnlaceVerificaLaCuentaYSirveUnaSolaVez`, `elTokenDeUnaCuentaSoloVerificaEsaCuenta`, `verificaSinSesionYNoDevuelveUnTokenDeSesion`, `verificarYReenviarALaVezNoSeTraban` | `verifica el token y ofrece ir a la tienda`, `avisa mientras verifica`, `la verificación del correo es pública`, `verifica el correo con el token del enlace` |
| RF-5 | `tokenVencidoInexistenteVacioODeCuentaDesactivadaDanElMismoError`, `cuentaDesactivadaNoRecibeElEnlace…` | `un enlace que no sirve lo dice…`, `sin token muestra el mismo mensaje sin llamar a la API`, `otro rechazo del enlace…`, `rechaza la verificación con TOKEN_VERIFICACION_INVALIDO` |
| RF-6 | `sinElCorreoVerificadoNoConfirmaNiReservaStock`, `alVerificarElCorreoYaPuedeConfirmar` | `sin el correo verificado no muestra el formulario…`, `el rechazo por el correo sin verificar lo avisa…` |
| RF-7 | `elCarritoDiceSiElCorreoEstaVerificado` | `sin el correo verificado avisa en lugar de ofrecer confirmar`, `…puede cambiar cantidades y quitar líneas` |
| RF-8 | `losDatosPersonalesDicenSiElCorreoEstaVerificado` | `dice que el correo está verificado…`, `sin verificar ofrece reenviar el correo y avisa` |
| RF-9 | `reenviarMandaUnEnlaceNuevoEInvalidaElAnterior`, `dosReenviosALaVezDejanUnSoloEnlaceVigente`, `reenviarSinSesionResponde401`, `verificarYReenviarALaVezNoSeTraban` | `Reenviar el correo avisa y no se manda dos veces…`, `reenvía el correo de verificación` |
| RF-10 | `elCuartoReenvioEn15MinutosResponde429SinEnviar` | `el límite de reenvíos dice que espere` |
| RF-11 | `reenviarConElCorreoVerificadoResponde409SinEnviar` | `si el correo ya estaba verificado recarga el carrito`, `…lo muestra sin perder lo escrito` |
| RF-12 | `cuentaDesactivadaNoRecibeElEnlaceYSuEnlaceAnteriorNoSirveNiAlReactivarla` | — |
| RF-13 | `unFalloDeLaVerificacionSeRegistraSinPropagarseNiMostrarElToken` | — |
| RF-14 | `laVerificacionLlevaElAsuntoYElEnlaceEnTextoYEnHtml`, `laVerificacionEscapaElNombreEnElHtml`, `elHtmlDeLaVerificacionNoLlevaTabulaciones` | — (prueba manual con Gmail) |
| RF-15 | `alVerificarElCorreoYaPuedeConfirmar`, `elCarritoDiceSiElCorreoEstaVerificado` | `con el correo verificado ofrece confirmar` |
| RF-16 | — (la respuesta no trae el token: `verificaSinSesion…`) | `no muestra el token` |

### Suites

- Backend: **577 pruebas, 0 fallas, 1 omitida** (antes 549).
- Frontend: **719 pruebas** (antes 695); `npx ng build` sin avisos.

### Revisiones

**Interfaz (subagente, 18 capturas):** corregidos el mensaje del reenvío
alineado con el aviso en celular, el salto de la página al llegar ese
mensaje, la etiqueta «Correo» alineada en «Mis datos» y el correo largo en
celular. Descartados: un tamaño de letra sin token (12,5 px) y, por el
presupuesto de 6 kB del SCSS del carrito, el atenuado del botón mientras
envía (queda «Enviando…») y la separación del contorno de foco.

**`/code-review` ronda 1 (9, 5 corregidos):** reactivar una cuenta revivía
su enlace (desactivar ahora los invalida); reenviar con la cuenta
desactivada decía que el correo salió; el HTML del correo llevaba
tabulaciones; en `/verificacion` un 4xx distinto ofrecía «Reintentar»; faltaba
la prueba del borde de 24 h. Descartados: el correo sale antes del commit
(mismo patrón que la recuperación, pendiente), una consulta de más al
registrarse, `findById` en el carrito, y el reenvío repetido en dos páginas
(decisión del plan).

**`/code-review` ronda 2 (10, 5 corregidos):** en «Mis datos», el 409 recargaba
la cuenta y borraba lo no guardado; la región `aria-live` se ocultaba vacía;
el foco se perdía al reintentar; `@Transactional` en `invalidarPendientes`;
`cursor: pointer` en el botón del carrito. Pendientes (abajo) los demás.

**`/security-review`:** sin hallazgos.

### Prueba manual

*21/10/2026.* El usuario probó con Docker y `TIENDA_CORREO_PROVEEDOR=smtp`:
el registro, el correo real por Gmail con su enlace, el aviso del carrito y
«Mis datos». **Todo pasó.**

### Distinto de lo planeado

- `reenviar` también rechaza una cuenta desactivada
  (`CuentaDesactivadaException`) y devuelve la cuenta, para el mensaje con el
  correo.
- Desactivar una cuenta (`GestionUsuariosService.cambiarEstado`) invalida sus
  enlaces de verificación (ronda 1).
- `/verificacion` ofrece «Reintentar» solo con un error de red o del servidor
  (status 0 o ≥ 500); cualquier otro rechazo dice que el enlace no sirve.
- «Mis datos», con 409, marca «Verificado» sin volver a pedir la cuenta.
- Nombres de las pruebas más largos que en el plan (la tabla de arriba manda).

### Pendientes

- `RecuperacionContrasenaService` cruza los bloqueos (`restablecer` bloquea el
  enlace antes que la cuenta), lo mismo que se corrigió acá.
- El correo (verificación y recuperación) se envía antes del commit: si la
  transacción falla después, llega un enlace que no sirve. Se arregla
  enviando después del commit (`@TransactionalEventListener`).
- Si la red se corta justo después de verificar, «Reintentar» dice que el
  enlace no sirve aunque el correo ya quedó verificado.
- Reenviar con la cuenta recién desactivada responde 403 y no 401: la sesión
  se cierra en la siguiente acción.
- El reenvío está repetido en el carrito y en «Mis datos», y los dos correos
  SMTP repiten su armado.
