# Arreglos de la auditoría del 18/11/2026

Issue #121. Rama `fix/auditoria-18-11`. Historias: HU-23 (RF-31 a RF-36,
ampliación), con notas en HU-13 (RF-8), HU-18 (RF-16) y HU-03 (RF-13).
Decisiones: `decisiones.md` → «Pagos», «Reportes» y «Venta en tienda»
(18/11/2026). La auditoría es de solo lectura: sus hallazgos, con
archivo:línea, son los once puntos aprobados por Fabio. Los planes viejos no
se tocan.

## Decisiones de diseño

- **Cancelar consulta antes, sin cambiar la respuesta (RF-31).** Si la
  consulta encuentra el pago, la cancelación responde el 409
  `PEDIDO_NO_PENDIENTE` de siempre, y la pantalla ya lo maneja: consulta,
  ve «Entregado» y va al comprobante (`VentaPage.cancelarQr` →
  `consultar` → `alCambiar`). **Motivo:** no hay forma nueva de respuesta
  ni código nuevo en la pantalla, y el 409 dice la verdad (ya no espera el
  pago).
- **Si el sistema de pago no responde, se cancela igual.** **Motivo:** el
  cliente está enfrente y el Empleado tiene que poder cobrarle en efectivo;
  con RF-32, un pago que llegue después no revive la venta: queda para
  devolver. Al vencer es al revés (no vence sin respuesta) porque ahí no
  espera nadie. Fabio lo confirmó (D2).
- **Pago a devolver solo para la venta cancelada (RF-32).** La vencida sigue
  con RF-11, como lo aprobó Fabio y lo confirmó en D1.
- **Un aviso a la vez por cobro, con una vuelta más (punto 6).** Una pieza
  chica, `UnaConsultaPorCobro`, que se prueba sin Spring. **No se limita
  por IP:** los avisos legítimos llegan desde los servidores del proveedor,
  y un límite por IP los frenaría a ellos (`decisiones.md` → «Pagos»).
  Solo los avisos pasan por ella: la página ya tiene `enConsulta` (una a la
  vez, sin vuelta extra), y la tarea y el vencimiento consultan siempre.
- **`url_retorno`, un parámetro más de `IPago.generarCobro` (punto 7):**
  `String rutaDeRetorno`, relativa al sitio, o `null`. Con `null` el
  adaptador manda la portada (`urlDelSitio()`), no omite el campo: no
  sabemos si el proveedor lo exige, y el manual no se copia al repositorio.
  **Motivo:** la ruta de «Mis pedidos» es de la tienda, no del proveedor, y
  la decide quien cobra.
- **«En línea» como medio del resumen (RF-35):** un enum del DTO,
  `MedioDelResumen { EN_LINEA, QR, EFECTIVO }`, y no un valor nuevo de
  `MedioPago`, que es de la entidad y se guarda solo en el mostrador. El
  orden de las filas sigue al de «Por canal»: En línea primero.
- **Clientes inactivos (RF-36):** la última compra, los pedidos y el total
  se calculan sobre todos los canales; el canal solo elige quién aparece
  (los que compraron alguna vez en ese canal). Fabio lo confirmó (D3).

## Tareas

Primero lo que se prueba con pruebas unitarias o de una sola clase (1 a 5),
después lo que necesita prueba de integración (6 y 7), y al final el
frontend (8 a 11). Las tareas 3 y 6 tocan código compartido (`IPago`,
`ConciliacionDePagos`): suite completa del backend al terminar cada una.

### Backend, sin prueba de integración

1. **El correo de recuperación, de usted** (`service/NotificadorCorreoSmtp.java`,
   `enviarRecuperacion`; HU-03 RF-13).
   - Asunto «Recupere su contraseña de {marca}». Texto y HTML: «…la
     contraseña de su cuenta…», «abra este enlace», «O copie este enlace en
     su navegador», «Si no pidió recuperar su contraseña, ignore este
     correo: su contraseña sigue siendo la misma».
   - Pruebas (`NotificadorCorreoSmtpTest`), primero:
     - `laRecuperacionVaAlUsuarioDesdeElRemitenteDelSitio`: el asunto nuevo
       (HU-03 RF-13).
     - `laRecuperacionTrataDeUsted` (nueva): ni el texto ni el HTML llevan
       «tu », «abre», «copia» ni «pediste» (HU-03 RF-13).
   - Revisar también `NotificadorCorreoSimulado` (lo que escribe en el
     registro) por el mismo tono.

2. **La ventana de 48 horas** (`service/ConciliacionAutomaticaDePagos.java:23`;
   HU-13 RF-8).
   - `VENTANA = Duration.ofHours(48)`, y el comentario de la clase y el de
     `CobroRepository.idsPorConciliar`.
   - Pruebas, primero:
     - `CobroRepositoryTest.idsPorConciliarSoloPendientesDeLasUltimas24Horas`
       → `…48Horas`: viejo a 49 h, reciente a 30 h (HU-13 RF-8).
     - `PagoIntegracionTest.laTareaConciliaLosDeLasUltimas24Horas` →
       `…48Horas`: avanzar 49 h; uno de 30 h sí se concilia (HU-13 RF-8).
       Es de integración pero solo cambia números: va acá.
     - `elCobroDeLas2345SePuedePagarHastaLas2359DelDiaSiguiente` (nueva,
       `PagoIntegracionTest`): cobro a las 23:45, pago a las 23:50 del día
       siguiente, la tarea lo aplica (HU-13 RF-8).

3. **`url_retorno` según quien cobra** (`service/pago/IPago.java`,
   `AdaptadorLibelula.java:64`, `SimuladorDePago.java`, `PagoService.java:76`,
   `VentaTiendaService.java:145`).
   - `CobroGenerado generarCobro(Long idOperacion, BigDecimal monto, String
     concepto, String rutaDeRetorno)`: `PagoService` pasa
     `"/mis-pedidos/" + idPedido`; `VentaTiendaService`, `null`. El
     adaptador: `cuenta.urlDelSitio() + (rutaDeRetorno == null ? "/" :
     rutaDeRetorno)`.
   - Cambio mecánico en las pruebas que simulan `generarCobro` (unas 25
     llamadas en `PagoIntegracionTest`, `VentaTiendaIntegracionTest`,
     `VentasDePrueba`, `SimuladorDePagoTest`, `LibelulaConfigTest`,
     `AdaptadorLibelulaTest`): un `any()` más.
   - Pruebas (`AdaptadorLibelulaTest`), primero:
     - `registraLaDeudaConLosDatosDelCobro`: sigue con
       `https://tienda.test/mis-pedidos/40` (HU-13 RF-13).
     - `sinRutaDeRetornoVuelveALaPortada` (nueva):
       `$.url_retorno == "https://tienda.test/"` (HU-13 RF-13).
     - `VentaTiendaIntegracionTest.elQrDelMostradorNoVuelveAMisPedidos`
       (nueva): `verify(simulador).generarCobro(any(), any(), any(),
       isNull())` (HU-23 RF-7).

4. **Un aviso a la vez por cobro** (`service/UnaConsultaPorCobro.java`, nueva;
   `service/PagoService.java:95-101`; HU-13 RF-10, RF-11).
   - **Delicado (concurrencia):**

     ```java
     // Una consulta por cobro a la vez (decisiones.md → Pagos, 18/11/2026). La que llega mientras otra consulta no
     // espera: le pide una vuelta más y vuelve. Así un pago que llegó durante la consulta en curso no se pierde, y
     // cien avisos del mismo cobro son como mucho dos consultas seguidas, nunca cien a la vez
     final class UnaConsultaPorCobro {

     	private final ConcurrentHashMap<Long, Boolean> enCurso = new ConcurrentHashMap<>();

     	void ejecutar(Long idCobro, Runnable consulta) {
     		// false: nadie pidió otra vuelta todavía
     		while (enCurso.putIfAbsent(idCobro, Boolean.FALSE) != null) {
     			if (enCurso.replace(idCobro, Boolean.TRUE) != null) {
     				return; // la que consulta hará otra vuelta
     			}
     			// La otra terminó entre las dos líneas: se intenta ser la que consulta
     		}
     		try {
     			do {
     				consulta.run();
     				// remove(clave, false) es atómico: sale solo si nadie pidió otra vuelta mientras consultaba
     			} while (!enCurso.remove(idCobro, Boolean.FALSE) && enCurso.replace(idCobro, Boolean.FALSE) != null);
     		} catch (RuntimeException error) {
     			enCurso.remove(idCobro);
     			throw error;
     		}
     	}
     }
     ```
   - `PagoService.procesarAviso`: `cobroRepository.idPorReferencia(referencia)
     .ifPresent(id -> avisos.ejecutar(id, () -> conciliacionDePagos.conciliar(id)))`.
     `UnaConsultaPorCobro` se crea en el constructor (no es un bean).
   - Pruebas (`UnaConsultaPorCobroTest`, nueva, con hilos y `CountDownLatch`),
     primero:
     - `unaSolaConsultaALaVezPorCobro`: 10 avisos mientras la primera está
       bloqueada en un latch → como mucho una consulta corriendo (HU-13 RF-11).
     - `lasQueLleganDuranteLaConsultaPidenUnaSolaVueltaMas`: al soltar el
       latch, dos consultas en total, no once (HU-13 RF-10).
     - `cobrosDistintosConsultanEnParalelo` (HU-13 RF-10).
     - `siLaConsultaFallaElCobroQuedaLibre`: la siguiente consulta corre
       (HU-13 RF-10).
     - `elAvisoAdelantaLaConsulta` (`PagoIntegracionTest`, ya existe) sigue
       en verde.

5. **El reporte completo sin repetir `lineasPorProducto`**
   (`service/ReportesService.java:108-118, 196-211`; pendiente del plan de
   HU-19).
   - `productos(Periodo, TipoCliente, Canal, boolean)` pasa a recibir el
     `Map<Long, LineasFila>` ya leído; `completo()` lo lee una vez y lo pasa
     a los dos rankings, y `productos(...)` público lo lee para su página.
   - Refactor sin cambio de salida: lo cubren
     `ReportesExportarIntegracionTest` y `ReportesListasIntegracionTest`, que
     tienen que seguir en verde sin tocarlas.

### Backend, con prueba de integración

6. **Cancelar consulta antes, y la cancelada queda para devolver**
   (`service/VentaTiendaService.java:237-246`,
   `service/ConciliacionDePagos.java:107-108`, `service/PedidoService.java:300-302`;
   HU-23 RF-31, RF-32).
   - **Delicado (pagos y bloqueos).** `cancelar` deja `@Transactional` y usa
     `transaccion`, como `consultar`: la consulta va sin nada bloqueado ni
     transacción abierta, y el bloqueo de la venta, después.

     ```java
     // RF-9, RF-31: antes de cancelar se pregunta al sistema de pago, sin nada bloqueado. Si el pago ya llegó, la
     // conciliación la deja entregada y abajo responde 409, como cualquier venta que ya no espera el pago. Si el
     // sistema de pago no responde, se cancela igual: un pago que llegue después queda para devolver (RF-32)
     public VentaResponse cancelar(Long idPedido) {
     	Optional<Long> cobro = transaccion.execute(estado -> {
     		Pedido venta = pedidoRepository.findById(idPedido).filter(Pedido::esEnTienda)
     				.orElseThrow(VentaNoEncontradaException::new);
     		if (venta.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
     			throw new PedidoNoPendienteException();
     		}
     		return pedidoService.cobroPendiente(idPedido).map(Cobro::getIdCobro);
     	});
     	cobro.ifPresent(conciliacionDePagos::conciliar);
     	return transaccion.execute(estado -> {
     		Pedido venta = bloquearVenta(idPedido);
     		if (venta.getEstado() != EstadoPedido.PENDIENTE_PAGO) {
     			throw new PedidoNoPendienteException(); // RF-31: el pago llegó con la consulta, o otro la cerró
     		}
     		cerrar(venta, EstadoPedido.CANCELADO);
     		return respuesta(venta, LocalDateTime.now(clock));
     	});
     }
     ```
   - `ConciliacionDePagos.aplicar`, rama `PAGADO` con el pedido ya cerrado,
     con la venta ya bloqueada (el orden de bloqueo no cambia):

     ```java
     } else if (pedido.esEnTienda() && pedido.getEstado() == EstadoPedido.CANCELADO) {
     	// HU-23 RF-32: la cancelada se cobró de otra forma o el cliente se fue: el pago se devuelve fuera del sistema
     	pedido.marcarPagoADevolver(fecha);
     } else {
     	pedidoService.revivir(pedido, fecha, ahora); // RF-9; HU-23 RF-11, solo la vencida
     }
     ```
   - `PedidoService.revivir`: sin cambios de lógica; el comentario de la
     línea 300 dice que del mostrador solo llega la vencida.
   - Pruebas (`VentaTiendaIntegracionTest`), primero:
     - `cancelarUnaVentaYaPagadaNoLaCancela` (nueva): `elBancoPaga` sin
       consultar, cancelar → 409 `PEDIDO_NO_PENDIENTE`, estado `ENTREGADO`,
       stock sin devolver, sin «Liberación por pedido» (RF-31).
     - `cancelarConElSistemaDePagoCaidoCancelaIgual` (nueva):
       `doThrow(SistemaDePagoNoDisponibleException)` en `consultar` → 200
       `CANCELADO` y el stock vuelve (RF-31).
     - `cancelarDevuelveElStock` (ya existe): sigue en verde (RF-9).
     - `elPagoDeUnaVentaCanceladaQuedaParaDevolver` (nueva): cancelar,
       `elBancoPaga`, conciliar → `PAGO_A_DEVOLVER`, stock como estaba
       después de cancelar, aunque alcance (RF-32).
     - `elPagoTardioReservaDeNuevoOQuedaParaDevolver` (ya existe): pasa a
       **vencer** las dos ventas con el reloj en lugar de cancelarlas; así
       sigue probando RF-11 (revive o para devolver) solo para la vencida.
     - `PagoIntegracionTest.elPagoTardioDeUnaVentaDelMostradorNoTocaElCarrito`
       (ya existe, con una vencida): sigue en verde (RF-11).
     - `cancelarYPagarALaVezTerminaEntregadoOParaDevolver` (nueva): dos hilos,
       cancelar y conciliar a la vez → `ENTREGADO` o (`CANCELADO` y después
       `PAGO_A_DEVOLVER`), nunca `PAGADO`, y el stock cuadra en los dos
       casos (RF-31, RF-32; casos límite).
     - `elPagoDeUnaVentaCanceladaNoMandaCorreos`: `verify(notificador,
       never())` de los dos correos del pedido (HU-23: sin correos).

7. **Reportes: «En línea» en los medios y clientes inactivos de todos los
   canales** (`dto/reportes/MedioDelResumen.java`, nuevo;
   `dto/reportes/VentaFila.java`, `dto/reportes/VentasResponse.java:49`,
   `service/ReportesService.java:341-348`,
   `repository/ReportesRepository.java:98-108`; HU-23 RF-35, RF-36).
   - `enum MedioDelResumen { EN_LINEA, QR, EFECTIVO }`.
     `VentaFila.medioDelResumen()`: `EN_LINEA` si `canal == EN_LINEA`; si
     no, `QR` o `EFECTIVO` según `medio`. `PorMedio(MedioDelResumen medio,
     …)`. `porMedio` recorre `MedioDelResumen.values()`.
   - `comprasHasta`: el canal sale del `where` y pasa a un `exists`, para
     que el `having` vea todas las compras del cliente:

     ```sql
     select new …CompraFila(u.idUsuario, max(p.pagado), count(p), sum(p.total))
     from Pedido p join p.usuario u
     where p.estado in :estados and u.rol = :rol and u.estado = :cuenta
       and (:tipo is null or u.tipoCliente = :tipo)
       and (:canal is null or exists (select 1 from Pedido q where q.usuario = u
            and q.canal = :canal and q.estado in :estados))
     group by u.idUsuario
     having max(p.pagado) <= :limite
     ```
   - Pruebas (`ReportesIntegracionTest`), primero:
     - `filtraPorCanalYRepartePorMedio` (ya existe): `$.porMedio[*].medio` →
       `["EN_LINEA", "QR", "EFECTIVO"]`, con la compra en línea en
       `EN_LINEA` y el QR solo del mostrador (RF-35).
     - `losTresMediosAparecenSinVentas` (nueva): período vacío → tres filas
       en cero (RF-35).
     - `sinVenderseYClientesInactivosPorCanal` (ya existe): sigue en verde
       (Ana, que solo compró en línea, no aparece con «En tienda»; RF-36).
     - `quienComproEnOtroCanalNoQuedaInactivo` (nueva): Ana compró en línea
       hace 40 días y en el mostrador hace 5 → no aparece en 30 días ni con
       «En línea» ni sin canal; con «En tienda», tampoco (RF-36).
     - `losPedidosYElTotalDelInactivoCuentanTodosLosCanales` (nueva): Beto,
       en línea hace 40 días y en el mostrador hace 50 → con «En línea»
       aparece con 2 pedidos y la suma de los dos (RF-36).
     - `ReportesExportarIntegracionTest`: el Excel de «Clientes inactivos»
       con canal sigue la misma regla (RF-36).
   - La consulta se prueba en H2; en MySQL, a mano, en la prueba manual
     (como las demás consultas de reportes).

### Frontend

8. **«Nueva venta» avisa antes de salir, y «Cancelando…»**
   (`features/venta/venta.page.ts`, `venta.page.html`, `qr-de-venta.html`,
   `app.routes.ts:185`; HU-23 RF-31, RF-33).
   - `VentaPage implements ConCambiosSinGuardar`, con `DialogoConfirmacion` y
     `host: { '(window:beforeunload)': 'alCerrarLaPestana($event)' }`, como
     `MisDatosPage`. Ruta `ventas/nueva` con
     `canDeactivate: [cambiosSinGuardarGuard]`.
   - `tieneCambiosSinGuardar()`: `!this.cobrada() && (this.conQr() !== null
     || this.lineas().length > 0)`. **Delicado:** `cobrada` es una señal que
     se pone en `true` **antes** de navegar al comprobante (en
     `cobrarEnEfectivo` y en `alCambiar` con `ENTREGADO` o `PAGADO`); si no,
     el guard preguntaría al ir al comprobante de la venta recién cobrada.
   - `confirmarSalida()`: con QR, `{ titulo: 'Hay un cobro con QR en curso',
     mensaje: 'Si sale, la venta sigue esperando el pago hasta que venza a
     los 10 minutos.', confirmar: 'Salir', desistir: 'Quedarme' }`; con solo
     líneas, `{ titulo: 'La venta no se cobró', mensaje: 'Si sale ahora, se
     pierden los productos armados.', confirmar: 'Salir sin cobrar',
     desistir: 'Seguir con la venta' }`. Textos de usted (`PRODUCT.md`).
   - `qr-de-venta.html`: «Cancelar venta» dice «Cancelando…» mientras
     `cancelando()` (ahora puede tardar hasta 15 s).
   - Pruebas (`venta.page.spec.ts`), primero:
     - `RF-33: sin líneas no pregunta al salir`
     - `RF-33: con líneas armadas pregunta, y «Seguir con la venta» se queda`
     - `RF-33: con el QR a la vista pregunta con su propio texto`
     - `RF-33: después de cobrar en efectivo va al comprobante sin preguntar`
     - `RF-33: con el QR pagado va al comprobante sin preguntar`
     - `RF-33: al cerrar la pestaña con líneas, beforeunload se cancela`
     - `RF-31: si al cancelar el pago ya llegó (409), va al comprobante`
     - `RF-31: «Cancelar venta» dice «Cancelando…» mientras espera`

9. **«Imprimir» solo en una venta hecha** (`features/comprobante/comprobante.page.ts`,
   `comprobante.page.html:13`; HU-23 RF-34).
   - `protected readonly imprimible = computed(() => ['ENTREGADO', 'PAGADO',
     'ANULADO'].includes(this.venta()?.estado ?? ''))`; `@if (imprimible())`
     alrededor del botón.
   - Pruebas (`comprobante.page.spec.ts`), primero:
     - `RF-34: «Imprimir» en Entregado, Pagado y Anulado`
     - `RF-34: sin «Imprimir» en Pendiente, Cancelado, Vencido y Pago a
       devolver`
     - `muestraElTicketCompleto` (ya existe): sigue en verde.

10. **«En línea» en el resumen** (`core/modelos/reportes.ts:197`,
    `features/reportes/reportes-resumen.ts:98-104`; HU-23 RF-35).
    - `type MedioDelResumen = 'EN_LINEA' | 'QR' | 'EFECTIVO'` y
      `NOMBRES_DE_MEDIO_DEL_RESUMEN` (`En línea`, `QR`, `Efectivo`) en
      `core/modelos/reportes.ts`; `porMedio` los usa.
    - Prueba (`reportes-resumen.spec.ts`), primero: la de HU-23 RF-22 pasa a
      `RF-35: «Por medio de pago» muestra En línea, QR y Efectivo`.

11. **El rótulo «Correo» cortado en `/usuarios` en el celular**
    (`features/usuarios/usuarios.page.html:102-108`, `usuarios.page.scss:18`;
    pendiente del plan de HU-18, HU-05).
    - **Primero reproducir** con `playwright-cli` (`--device="iPhone 15"`,
      un correo largo) y captura (`systematic-debugging`). **Hipótesis:**
      la celda es `display: flex` (`styles/_lista.scss`) y el correo partido
      con `<wbr>` son dos textos sueltos, que pasan a ser dos elementos flex
      junto al `::before`; `overflow-wrap: anywhere` de `.correo` también
      llega al `::before`. **Arreglo propuesto:** el correo dentro de un
      `<span class="correo">` (el `overflow-wrap` pasa al `span`) y la celda
      con solo el rótulo y el `span`. Si la causa es otra, se anota en el
      «Resultado».
    - Prueba (`usuarios.page.spec.ts`): `el correo va en un solo elemento
      junto al rótulo` (la estructura). Lo visual se confirma con la captura
      en celular.

12. **Cierre.** `/sdd:cerrar HU-23` con las revisiones de `docs/flujo-sdd.md`:
    subagente de interfaz (venta, comprobante, reportes y `/usuarios` en
    celular), `/code-review` y `/security-review` (pagos y un endpoint
    público: el aviso). Prueba manual de los escenarios nuevos de HU-23 con
    el simulador; RF-31 y RF-32 también con Libélula real si hay tiempo.
    `docs/lista-de-cambios.md` § 4: la pregunta de «En línea» queda
    respondida.

## Cobertura de los RF

| RF | Pruebas |
|---|---|
| HU-23 RF-31 | `cancelarUnaVentaYaPagadaNoLaCancela`, `cancelarConElSistemaDePagoCaidoCancelaIgual`, `cancelarYPagarALaVezTerminaEntregadoOParaDevolver`; front «si al cancelar el pago ya llegó…», «Cancelando…» |
| HU-23 RF-32 | `elPagoDeUnaVentaCanceladaQuedaParaDevolver`, `cancelarYPagarALaVez…`, `elPagoDeUnaVentaCanceladaNoMandaCorreos` |
| HU-23 RF-33 | las seis de `venta.page.spec.ts` |
| HU-23 RF-34 | las dos de `comprobante.page.spec.ts` |
| HU-23 RF-35 | `filtraPorCanalYRepartePorMedio`, `losTresMediosAparecenSinVentas`; front «Por medio de pago» |
| HU-23 RF-36 | `quienComproEnOtroCanalNoQuedaInactivo`, `losPedidosYElTotalDelInactivoCuentanTodosLosCanales`, `sinVenderseYClientesInactivosPorCanal`, exportación |
| HU-13 RF-8 | `…48Horas` (repositorio e integración), `elCobroDeLas2345…` |
| HU-13 RF-10, RF-11 | `UnaConsultaPorCobroTest` (cuatro), `elAvisoAdelantaLaConsulta` |
| HU-13 RF-13 | `sinRutaDeRetornoVuelveALaPortada`, `registraLaDeudaConLosDatosDelCobro` |
| HU-03 RF-13 | `laRecuperacionTrataDeUsted`, el asunto nuevo |

## Análisis antes de programar (como `/sdd:analizar`, 18/11/2026)

Revisados la historia ampliada, este plan, `CLAUDE.md`, `PRODUCT.md` y
`decisiones.md`. Corregido en este mismo análisis:

- **RF-32 y la prueba existente:** `elPagoTardioReservaDeNuevoOQuedaParaDevolver`
  cancela las dos ventas y espera que una reviva; con RF-32 fallaría. Se
  cambia a vencerlas (tarea 6), y así sigue cubriendo RF-11.
- **El guard y la navegación al comprobante:** con «hay líneas» como único
  criterio, cobrar en efectivo preguntaría antes de ir al comprobante. Se
  agregó la señal `cobrada` y dos pruebas (tarea 8).
- **`url_retorno` sin dato:** omitirlo podía hacer fallar el registro de la
  deuda (no sabemos si el proveedor lo exige); se manda la portada (tarea 3).
- **Correos:** «Pago a devolver» de una venta del mostrador no manda
  correo; `ConciliacionDePagos` ya sale antes del correo con `esEnTienda()`,
  y `CLAUDE.md` → «Correos» no cambia. Se agregó la prueba.
- **HU-14 y el comprobante del Cliente:** «Pago a devolver» ya no tiene
  comprobante (RF-12), coherente con RF-34. Sin cambios.
- **Orden de bloqueo:** la cancelación consulta sin nada bloqueado y
  después bloquea la venta, cobro y productos, como `consultar`. La rama
  nueva de la conciliación no bloquea productos. No cambia
  `decisiones.md` → «Venta en tienda».

Sin hallazgos críticos. Las tres dudas, respondidas por Fabio el 18/11/2026:

- **D1, la venta vencida:** sigue con RF-11, sin cambios. En el mostrador
  el producto no se entrega mientras no se vea el pago. Si el QR vence, el
  Empleado cobra en efectivo y después llega el pago del QR, la venta se
  deshace a mano y se procesa como una venta en efectivo: el sistema no se
  encarga de eso, porque es muy difícil que pase.
- **D2, cancelar con el sistema de pago caído:** se cancela igual, como
  dice el plan.
- **D3, «Clientes inactivos» con canal:** como dice el plan. Aparecen los
  que compraron alguna vez en ese canal, con la última compra, los pedidos
  y el total de todos los canales.

## Resultado

*19/11/2026.* Tareas 1 a 11 hechas, en el orden del plan, con las pruebas
primero (las de las tareas 6 y 8 se vieron fallar antes del código).

**Suites:** backend 875 pruebas, 0 fallas (`./mvnw test`); frontend 992
pruebas, `ng lint` y `ng build` sin avisos.

**Cobertura:** la de la tabla de arriba, con dos diferencias:

- `elPagoDeUnaVentaCanceladaNoMandaCorreos` no es una prueba aparte: sus
  dos `verify(notificador, never())` van en
  `elPagoDeUnaVentaCanceladaQuedaParaDevolver`.
- RF-36 en el Excel no tiene prueba propia: la exportación usa la misma
  consulta (`ReportesService.completo` → `comprasHasta`), que cubren las
  pruebas de `ReportesIntegracionTest`.

**Distinto de lo planeado:**

- Tarea 11: no se reprodujo el rótulo cortado (iPhone 15 y 320 px, correos
  largos con y sin puntos, en modo oscuro). La causa supuesta sigue en el
  código (el correo eran dos textos sueltos en una celda flex, con el
  `overflow-wrap` también en el rótulo), así que se aplicó el arreglo
  propuesto: el correo en un `span.correo-texto`, que lleva el
  `overflow-wrap`.
- `UnaConsultaPorCobro` libera el cobro también ante un `Error`, no solo
  una `RuntimeException` (`/code-review`).

**Revisiones:**

- `/code-review` (subagente Fable): nada importante. Tres menores:
  - el `Error` que dejaba el cobro tomado (corregido);
  - la vuelta pedida mientras una consulta falla se pierde (queda comentado:
    la tarea de cada minuto lo concilia igual);
  - el comentario de la ventana decía que la deuda vence «al terminar el
    día siguiente»: vence a las 23:59 del día en que vence el pedido, hasta
    unas 24 h 15 min (comentarios corregidos; las 48 h se quedan, con
    margen).
- `/security-review` (subagente Fable): sin hallazgos. El aviso sigue sin
  confirmar nada por sí solo, `url_retorno` la arma el servidor y las rutas
  siguen con sus roles.
- **Interfaz (pendiente):** necesita la tienda levantada con esta rama;
  Docker corre ahora la de HU-24.

**Prueba manual:** pendiente. Fabio la hace al final, junto con las demás.
