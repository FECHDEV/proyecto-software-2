# Plan — HU-21 Los correos del pedido

Historia: `docs/historias/HU-21.md` · Issue #67 · Rama
`feature/hu-21-correos-pedido`

Solo backend. El pago se aplica en un solo lugar, `ConciliacionDePagos.aplicar`
(la consulta de la página, el aviso, la tarea de cada minuto, el vencimiento y
«Simular pago» pasan por ahí). Ahí, con el pedido ya «Pagado» o «Pago a
devolver», se arman los datos del correo y se encarga el envío con
`TrasElCommit`. Las pantallas no cambian.

## Decisiones técnicas

- **Dos métodos más en `NotificadorCorreo`** (como pide «Envío de correo
  detrás de `NotificadorCorreo`»), cada uno con su versión en el simulador y
  en el adaptador SMTP.
- **Los datos se copian dentro de la transacción** a un record inmutable
  (`CorreoDelPedido`), con las líneas ya leídas: el envío es `@Async` y corre
  sin sesión de Hibernate, así que no puede tocar entidades perezosas. El
  correo y el nombre salen del `Usuario` que `aplicar` ya bloqueó: el correo
  de la cuenta (no se puede cambiar), esté activa o desactivada.
- **Una sola vez por pedido (RF-5) sin nada nuevo:** `aplicar` ya sale si el
  cobro no está `PENDIENTE` (HU-13 RF-11), así que solo la conciliación que
  aplica el pago encarga el correo.
- **Después del commit (RF-6) y sin cambiar el pago si falla (RF-7):**
  `TrasElCommit.ejecutar`, como el registro del pago. El adaptador SMTP ya
  atrapa los fallos del envío y los deja en el log.
- **El retiro y el enlace los pone el adaptador:** inyecta `RetiroProperties`
  (las líneas vacías no se muestran) y arma «Ver mi pedido» con
  `CorreoProperties.urlDelSitio() + "/mis-pedidos/" + número`. La página pide
  la sesión, como cualquier pedido.
- **Formato como en la pantalla del pedido:** «Bs 1.234,50», la cantidad con
  los decimales de su unidad y « / metro» si la unidad no es «Unidad»
  (`2 × Bs 85,00`), y la fecha del pago `dd/MM/yyyy HH:mm`. El formato de
  números de `exportar/Celda` pasa a una clase `service/FormatoBolivia`
  (`dinero(BigDecimal)`, `cantidad(BigDecimal, int decimales)`), que usan los
  dos; así no se repite.
- **Asuntos, de usted:** «Pedido N.º 12 pagado en Tienda Montero» y «Pedido
  N.º 14: le devolveremos su pago». Texto plano y HTML con la plantilla de
  siempre; en el HTML se escapan el nombre del cliente, los nombres de
  producto (copia en `LineaPedido.nombre`), quien retira, el teléfono, la
  dirección y el horario (RF-8).
- **El simulador** escribe una línea por correo con el número, el destinatario
  y el total; sin el teléfono ni las líneas.

## Tarea 1 — Los correos en el notificador · backend

**Archivos:** `service/CorreoDelPedido.java` (nuevo), `service/FormatoBolivia.java`
(nuevo), `service/NotificadorCorreo.java`, `NotificadorCorreoSmtp.java`,
`NotificadorCorreoSimulado.java`, `service/exportar/Celda.java` (usa
`FormatoBolivia`).

```java
public record CorreoDelPedido(String correo, String nombre, Long numero, LocalDateTime fechaPago,
		List<Linea> lineas, BigDecimal total, String nombreRetira, String telefonoRetira) {
	public record Linea(String nombre, UnidadVenta unidad, BigDecimal cantidad, BigDecimal precio,
			BigDecimal subtotal) {}
	static CorreoDelPedido de(Pedido pedido, Usuario usuario) { … }
}
// NotificadorCorreo
void enviarPedidoPagado(CorreoDelPedido pedido);
void enviarPagoADevolver(CorreoDelPedido pedido);
```

**Pruebas** — `NotificadorCorreoSmtpTest`:

- `elPedidoPagadoVaAlClienteConSuAsunto` — RF-1
- `elPedidoPagadoLlevaLineasTotalQuienRetiraRetiroYEnlace` (texto y HTML) — RF-3
- `sinRetiroConfiguradoNoMuestraEsasLineas` y `conSoloElHorarioMuestraSoloEse` — RF-3, casos límite
- `lasCantidadesConDecimalesVanConSuUnidad` — RF-3, caso límite
- `elPagoADevolverLlevaNumeroMontoYElAvisoDelTelefono` — RF-4
- `losDatosDelPedidoSeEscapanEnElHtml` — RF-8, caso límite
- `siElEnvioDelPedidoFallaQuedaEnElLog` — RF-7

`NotificadorCorreoSimuladoTest`:
`losCorreosDelPedidoQuedanEnElLogSinElTelefono` — caso límite (simulador).

`CeldaTest` o las pruebas de la exportación ya existentes siguen en verde
con `FormatoBolivia` (refactor, sin RF nuevo).

## Tarea 2 — El envío al aplicar el pago · backend

**Archivos:** `service/ConciliacionDePagos.java` (inyecta `NotificadorCorreo`;
en `case PAGADO`, después de `pagar` o `revivir`, según `pedido.getEstado()`).

```java
// aplicar(): Usuario usuario = usuarioRepository.findConBloqueoByIdUsuario(…).orElseThrow();
case PAGADO -> {
	LocalDateTime fecha = resultado.fechaPago() == null ? ahora : resultado.fechaPago();
	cobro.registrarPago(fecha);
	if (pedido.getEstado() == EstadoPedido.PENDIENTE_PAGO) {
		pedido.pagar(fecha);
	} else {
		pedidoService.revivir(pedido, fecha, ahora); // HU-13 RF-9
	}
	// Dentro de la transacción: las líneas y el usuario ya están leídos y bloqueados (HU-21)
	CorreoDelPedido correo = CorreoDelPedido.de(pedido, usuario);
	boolean pagado = pedido.getEstado() == EstadoPedido.PAGADO;
	// Al confirmar: si algo falla antes, el cobro sigue pendiente y no sale nada (RF-6). Solo la conciliación
	// que encontró el cobro PENDIENTE llega acá (RF-5)
	TrasElCommit.ejecutar(() -> {
		log.info("Cobro {} pagado el {} (pedido {})", datos.idCobro(), fecha, datos.idPedido());
		if (pagado) {
			notificadorCorreo.enviarPedidoPagado(correo); // RF-1, RF-2
		} else {
			notificadorCorreo.enviarPagoADevolver(correo); // RF-4
		}
	});
}
```

**Pruebas** — `PagoIntegracionTest` (con `@MockitoSpyBean NotificadorCorreo`):

- `alPagarseElPedidoSeEnviaPedidoPagadoConSusDatos` — RF-1, RF-3
- `unPagoTardioQueReviveEnviaPedidoPagado` — RF-2
- `unPagoTardioSinStockEnviaPagoADevolverYNoPedidoPagado` — RF-4
- `dosConciliacionesALaVezEnvianUnSoloCorreo` — RF-5 (y volver a consultar un
  pedido ya pagado no envía otro)
- `siLaTransaccionDelPagoSeDeshaceNoSeEnviaElCorreo` — RF-6 (en un pago tardío,
  `PedidoService.revivir` falla con un spy y la transacción se deshace)
- `siElNotificadorFallaElPedidoQuedaPagado` — RF-7
- `conLaCuentaDesactivadaElCorreoSaleIgual` — caso límite
- `simularPagoTambienEnviaElCorreo` — RF-1 (el camino del simulador de pago)

## Revisiones (al cerrar)

Las de `docs/flujo-sdd.md` → «Revisión antes del PR». Sin pantallas: no hay
revisión de interfaz. **`/security-review`:** toca pagos y manda datos del
pedido (nombre, teléfono) a un correo. Prueba manual de Fabio: un pedido
pagado en la PC con el simulador de pago y Gmail real, mirando el correo en
escritorio y en el celular.

## Resultado

*26/10/2026.* Cerrada: la prueba manual de Fabio salió bien (pago con el
simulador y Gmail real; llegó el correo).

**Cobertura:**

| RF | Pruebas |
|---|---|
| RF-1 | `NotificadorCorreoSmtpTest.elPedidoPagadoVaAlClienteConSuAsunto`; `PagoIntegracionTest.alPagarseElPedidoSeEnviaPedidoPagadoConSusDatos`, `simularPagoTambienEnviaElCorreo` |
| RF-2 | `unPagoTardioQueReviveEnviaPedidoPagado` |
| RF-3 | `elPedidoPagadoLlevaLineasTotalQuienRetiraRetiroYEnlace`, `sinRetiroConfiguradoNoMuestraEsasLineas`, `conSoloElHorarioMuestraSoloEse`, `lasCantidadesConDecimalesVanConSuUnidad`, `elRetiroVaSinEspaciosDeMas` |
| RF-4 | `elPagoADevolverLlevaNumeroMontoYElAvisoDelTelefono`, `unPagoTardioSinStockEnviaPagoADevolverYNoPedidoPagado` |
| RF-5 | `dosConciliacionesALaVezEnvianUnSoloCorreo` |
| RF-6 | `siLaTransaccionDelPagoSeDeshaceNoSeEnviaElCorreo` |
| RF-7 | `siElEnvioDelPedidoFallaQuedaEnElLog`, `siElNotificadorFallaElPedidoQuedaPagado` |
| RF-8 | `losDatosDelPedidoSeEscapanEnElHtml` |
| Casos límite | `conLaCuentaDesactivadaElCorreoSaleIgual`, `losCorreosDelPedidoQuedanEnElLogSinElTelefono` (simulador) |

**Suites:** backend 725 pruebas, 0 fallas, 1 omitida; frontend 69 archivos,
842 pruebas (sin cambios); `ng build` sin avisos.

**`/security-review`:** sin hallazgos (datos escapados en el HTML, nada del
usuario en el asunto ni en el destinatario, sin endpoints nuevos).

**`/code-review` medium, ronda 1:**

- Corregidos: las continuaciones de los bloques de texto quedaron como ``
  literal y cortaban la frase de «Pago a devolver» (prueba nueva);
  `RetiroProperties` se registra en `ProgramacionConfig` y no colgada de
  `PedidoService`; `toLowerCase(Locale.ROOT)`; un solo `escapar()` (UTF-8)
  en todos los correos; sin el método intermedio de `Celda`.
- Descartados: el monto del cobro en lugar del total (el cobro se genera por
  el total guardado, que no cambia); escapar la URL del sitio (configuración
  de confianza, como en los otros correos); el contexto de pruebas extra por
  los espías (los necesitan las pruebas de RF-6 y RF-7).

**`/code-review` medium, ronda 2 (la última):**

- Corregidos: `%n` mezclaba finales de línea en Windows (`
`, con prueba);
  la dirección y el horario se normalizan una vez en `RetiroProperties`
  (prueba nueva); la prueba de RF-7 verifica que se intentó enviar; un record
  `Dato` en lugar de `String[]`; el import de `times`.
- Descartado: copiar las líneas también para «Pago a devolver» (costo mínimo
  dentro de la misma transacción).

**Distinto de lo planeado:** `FormatoBolivia` expone `numero(valor, mínimo,
máximo)` y `dinero(valor)`, no `cantidad(valor, decimales)`; el correo de la
cuenta no se puede cambiar, así que ese caso límite se quitó de la historia;
`RetiroProperties` quita los espacios de más y trata el vacío como sin
configurar.

**Confirmado por Fabio al cerrar:** con la cuenta desactivada, el correo
sale igual (anotado en `decisiones.md` → «Correos del pedido»).
