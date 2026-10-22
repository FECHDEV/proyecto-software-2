# Plan — HU-17 Entregar los pedidos pagados

Historia: `docs/historias/HU-17.md` · Issue #21 · Rama `feature/hu-17-entregar-pedidos`

Backend: el estado «Entregado» con quién y cuándo, la API del personal en
`/api/despacho/pedidos` (lista, ficha y entrega) y la búsqueda por nombre de
quien retira. Frontend: la lista `/pedidos`, la ficha `/pedidos/:id` con
«Registrar entrega», la tarjeta del panel y «Entregado» para el Cliente.

## Tarea 1 — «Entregado» y la entrega · backend

**Archivos:** `entity/EstadoPedido.java`, `entity/Pedido.java`,
`repository/PedidoRepository.java`, `service/DespachoService.java` (nuevo),
`controller/DespachoController.java` (nuevo), `config/SecurityConfig.java`,
`dto/PedidoDespachoResponse.java` (nuevo), `dto/PedidoResponse.java`;
excepción `PedidoNoEntregableException` (409 `PEDIDO_NO_ENTREGABLE`).

- `EstadoPedido.ENTREGADO`. `Pedido` suma `entregado` (`LocalDateTime`,
  nulo) y `entregadoPor` (`@ManyToOne(fetch = LAZY)` a `Usuario`, nulo), y
  `entregar(Usuario quien, LocalDateTime fecha)`, que lanza
  `IllegalStateException` si no está `PAGADO` (como `cerrar` y `pagar`: el
  servicio lo revisa antes, con el pedido bloqueado). No toca el stock (RF-6).
- **API** (`DespachoController`, `/api/despacho/pedidos`):
  - `GET /{id}` → `PedidoDespachoResponse(idPedido, estado, lineas, total,
    nombreRetira, telefonoRetira, nota, confirmado, pagado, entregado,
    entregadoPor, clienteNombre, clienteCorreo)`; `entregadoPor` es el
    nombre y el apellido. 404 `PEDIDO_NO_ENCONTRADO` si no existe.
  - `POST /{id}/entrega` → 200 con la ficha ya entregada; 409
    `PEDIDO_NO_ENTREGABLE`; 404.
  - Id con el mismo patrón que `PedidoController` (`\d{1,18}`): otra cosa es
    404 (RF-12).
- **`SecurityConfig`**, como el inventario: `GET /api/despacho/**` para
  Empleado, Administrador y Contador; lo demás de `/api/despacho/**`, solo
  Empleado y Administrador (RF-9).
- `PedidoResponse` (la página del Cliente) suma `entregado` (RF-10).

**Lo delicado: la entrega bloquea primero al cliente** (`decisiones.md` →
«Pedidos»), como la verificación de HU-20: el id del cliente se lee con una
proyección sin bloqueo que no carga el pedido; después se bloquean el cliente
y el pedido, en ese orden. Si el pedido se cargara antes, el bloqueo no lo
volvería a leer y dos entregas a la vez lo verían «Pagado».

```java
// PedidoRepository
@Query("select p.usuario.idUsuario from Pedido p where p.idPedido = :idPedido")
Optional<Long> idClienteDelPedido(@Param("idPedido") Long idPedido);

// DespachoService. READ_COMMITTED como todo lo que cambia un pedido (decisiones.md → «Pagos»)
@Transactional(isolation = Isolation.READ_COMMITTED)
public PedidoDespachoResponse entregar(Long idPedido, Long idPersonal) {
	Long idCliente = pedidoRepository.idClienteDelPedido(idPedido).orElseThrow(PedidoNoEncontradoException::new);
	usuarioRepository.findConBloqueoByIdUsuario(idCliente).orElseThrow(PedidoNoEncontradoException::new);
	Pedido pedido = pedidoRepository.findConBloqueoByIdPedidoAndUsuario_IdUsuario(idPedido, idCliente)
			.orElseThrow(PedidoNoEncontradoException::new);
	if (pedido.getEstado() != EstadoPedido.PAGADO) {
		throw new PedidoNoEntregableException(); // RF-7: ya entregado, pendiente, cerrado o con pago a devolver
	}
	pedido.entregar(usuarioRepository.getReferenceById(idPersonal), LocalDateTime.now(clock));
	return respuesta(pedido);
}
```

**Lo delicado: la columna `estado` en MySQL** (`decisiones.md` → «Base de
datos: sin migraciones», 21/10/2026). Hibernate creó `pedido.estado` como
`enum('CANCELADO', …, 'VENCIDO')` y `ddl-auto=update` no cambia columnas que ya
existen: un `ENTREGADO` fallaría en MySQL y pasaría en H2. Se mapea con
`@JdbcTypeCode(SqlTypes.VARCHAR)`, así una base nueva la crea `varchar(15)`, y
`service/EstadoPedidoAlArrancar.java` (nuevo) cambia la que ya existe:

```java
// Sin migraciones, una base de antes de HU-17 tiene pedido.estado como enum de MySQL, que no admite ENTREGADO: se
// pasa a varchar una sola vez. En H2 y en una base nueva no encuentra un enum y no hace nada
@Component
@RequiredArgsConstructor
public class EstadoPedidoAlArrancar {

	private final JdbcTemplate jdbc;

	@EventListener(ApplicationReadyEvent.class)
	public void ajustar() {
		List<String> tipos = jdbc.queryForList("""
				select data_type from information_schema.columns
				where table_schema = database() and table_name = 'pedido' and column_name = 'estado'""", String.class);
		if (tipos.stream().anyMatch("enum"::equalsIgnoreCase)) {
			jdbc.execute("alter table pedido modify estado varchar(15) not null");
		}
	}
}
```

Se comprueba con Docker (la base local tiene el `enum`) antes de la prueba
manual.

**Pruebas** (`DespachoIntegracionTest`, con el reloj de prueba):
- `entregarDejaElPedidoEntregadoConQuienYCuandoSinMoverElStock` — HU-17 RF-6
- `soloSeEntregaUnPedidoPagado` (pendiente, cancelado, vencido, con pago a
  devolver y ya entregado: 409 sin cambios) — HU-17 RF-7, RF-8
- `dosEntregasALaVezDejanUnaSola` (dos hilos) — HU-17 RF-7
- `entregarMientrasVenceOtroPedidoDelClienteNoSeTraba` (dos hilos: la
  entrega y el vencimiento de otro pedido del mismo cliente) — HU-17 RF-6
- `EstadoPedidoAlArrancarTest.enH2NoHaceNada` — técnico (la columna)
- `laFichaTraeLasLineasElClienteYQuienEntrego` (con el producto renombrado
  después de pagar: la línea conserva su nombre) — HU-17 RF-4
- `elContadorVeLaFichaPeroNoEntrega` y `sinSesion401YClienteRecibe403` — HU-17 RF-9
- `unPedidoQueNoExisteResponde404` — HU-17 RF-12
- `elClienteVeLaFechaDeEntregaEnSuPedido` — HU-17 RF-10
- `PedidoTest.entregarSoloDesdePagado` — HU-17 RF-6, RF-7

## Tarea 2 — La lista y la búsqueda · backend

**Archivos:** `entity/Pedido.java`, `repository/PedidoRepository.java`,
`service/DespachoService.java`, `controller/DespachoController.java`,
`service/BusquedaAlArrancar.java`, `dto/PedidoDespachoResumenResponse.java`
(nuevo).

- `GET /api/despacho/pedidos?estado=&texto=&pagina=` →
  `PaginaResponse<PedidoDespachoResumenResponse(idPedido, estado,
  confirmado, pagado, nombreRetira, cantidadProductos, total)>`, de a 10.
- **Estado:** sin parámetro o con uno que no existe, `PAGADO` (RF-2, RF-12);
  `TODOS`, sin filtro. Con `PAGADO`, por `pagado` ascendente; con otro o con
  `TODOS`, por `confirmado` descendente (y el id para desempatar).
- **Búsqueda (RF-3):** `Pedido.busquedaRetira`, el nombre de quien retira con
  `Producto.textoDeBusqueda` (sin tildes ni mayúsculas, igual en H2 que en
  MySQL, como HU-09); se llena al confirmar. Los pedidos que ya existían la
  reciben al arrancar en `BusquedaAlArrancar`. El texto se recorta a 100, se
  normaliza igual y va como patrón de `Busquedas.patron`; si son solo dígitos
  (hasta 18), también coincide el número del pedido.
- Una sola consulta por página, sin cargar las líneas (como `misPedidos`):
  `size(p.lineas)` en la proyección, con su `countQuery`.
- Página que no es un número, negativa o después de la última: como
  `misPedidos` (RF-12). El `numeroDePagina` de `PedidoController` pasa a un
  ayudante compartido por los dos controladores.
- La lista no vence pedidos: eso lo hace la tarea de cada minuto.

**Pruebas** (`DespachoIntegracionTest`):
- `sinFiltroListaLosPagadosElMasViejoPrimero` — HU-17 RF-1, RF-2
- `conOtroEstadoOTodosElMasNuevoPrimero` (un pendiente con la hora pasada
  sigue «Pendiente de pago»: la lista no vence) — HU-17 RF-2
- `buscaPorNumeroOPorNombreSinTildesNiMayusculas` (con un comodín `%` como
  texto) — HU-17 RF-3
- `unaBusquedaVaciaNoFiltraYUnaLargaSeRecorta` — HU-17 RF-3
- `unEstadoOUnaPaginaQueNoSirvenMuestranLoMasCercano` — HU-17 RF-12
- `elClienteNoVeLaLista` (403) y `elContadorVeLaLista` — HU-17 RF-9
- `BusquedaAlArrancarTest.completaLaBusquedaDeLosPedidosDeAntes` — HU-17 RF-3

## Tarea 3 — La lista, la ficha y «Entregado» · frontend

**Archivos:** `core/modelos/pedido.ts`, `core/modelos/despacho.ts`,
`core/api/despacho.service.ts` (y su `.spec`), `core/api/mensajes-error.ts`,
`features/despacho/` y `features/despacho-pedido/` (`.ts`, `.html`, `.scss`,
`.spec.ts`, nuevos), `app.routes.ts`, `features/panel/panel.page.html` (y
`.ts`), `features/pedido/pedido.page.html` (y `.ts`).

- Modelo: `ENTREGADO` en `EstadoDePedido` y en `NOMBRES_DE_ESTADO`
  («Entregado»); `entregado` en `Pedido`. `despacho.ts`, espejo de los DTO,
  con `FiltrosDeDespacho(estado, texto, pagina)`.
- `DespachoService.lista(filtros)` y `ficha(id)` con `httpResource`;
  `entregar(id): Promise<PedidoDespacho>` con `HttpClient`.
- Rutas `pedidos` y `pedidos/:id`, con `rolGuard` y
  `data: { roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] }`, como el
  inventario.
- **Lista** (como el inventario): el selector de estado (los seis y «Todos»,
  «Pagado» de entrada), la búsqueda con espera después de la última tecla, y
  los filtros y la página en la dirección (sin parámetros es «Pagado»). Cada
  fila: número, fecha, quien retira, chip, productos y total, con enlace a la
  ficha. Vacía: «No hay pedidos con ese filtro». Error de carga con
  «Reintentar».
- **Ficha:** los datos de RF-4 y, si está pagado y quien mira no es Contador,
  «Registrar entrega», que abre `app-dialogo-confirmacion` con el número, el
  nombre y el teléfono de quien retira. Al confirmar, la ficha se pone al día
  con la respuesta; con 409, se vuelve a pedir y muestra el mensaje. Mientras
  envía, el botón no se puede volver a tocar. Id que no sirve o 404: «No
  encontramos ese pedido» y el enlace a la lista.
- **Panel y encabezado:** la tarjeta «Pedidos» y el enlace del encabezado
  para los tres roles del personal (RF-11), como el inventario.
- **Cliente:** «Entregado» con su chip en «Mis pedidos», y en la página del
  pedido «Entregado el …» (RF-10).

**Pruebas:**
- `despacho.page.spec`: `lista los pedidos con quien retira, chip y enlace` —
  HU-17 RF-1; `de entrada filtra Pagado` y `cambiar el estado o buscar
  cambia la dirección` — HU-17 RF-2, RF-3; `sin pedidos lo dice` — HU-17
  RF-1; `una dirección que no sirve muestra lo más cercano` — HU-17 RF-12;
  `un error de carga deja reintentar` — caso borde.
- `despacho-pedido.page.spec`: `muestra los datos del pedido y del cliente` y
  `entregado dice quién y cuándo` — HU-17 RF-4; `Registrar entrega pide
  confirmar con nombre y teléfono` y `confirmar lo deja Entregado` — HU-17
  RF-5, RF-6; `no se envía dos veces mientras entrega` — caso borde; `con 409
  se pone al día y lo avisa` — HU-17 RF-7; `el Contador no ve Registrar
  entrega` — HU-17 RF-9; `un entregado no ofrece nada` — HU-17 RF-8; `un
  pedido que no existe lleva a la lista` — HU-17 RF-12.
- `panel.page.spec`: `la tarjeta Pedidos para el personal y no para el
  Cliente` y `app.component.spec`: `un empleado ve «Inicio», «Inventario» y
  «Pedidos»` — HU-17 RF-11.
- `app.routes.spec`: `pedidos es del personal` — HU-17 RF-9.
- `pedido.page.spec` y `mis-pedidos.page.spec`: `muestra Entregado con su
  fecha` — HU-17 RF-10.
- `despacho.service.spec`: las tres llamadas — HU-17 RF-1, RF-4, RF-6.

## Decisiones del plan

- **`/api/despacho/…`, no `/api/pedidos/…`:** `/api/pedidos/**` es solo del
  Cliente y el pedido sale del token; separar las rutas deja las reglas de
  `SecurityConfig` simples, como el inventario.
- **La entrega responde la ficha** y no un mensaje: la pantalla se pone al día
  sin otra llamada.
- **«Entregado» con el chip neutro:** el verde queda para «Pagado», que es lo
  que el Empleado busca en la lista. Amplía los colores de HU-14 RF-3 sin
  tocar esa historia.
- **Carpetas `despacho/` y `despacho-pedido/`,** como la API; la ruta sigue
  siendo `/pedidos`. Así no se confunden con `pedido/` y `mis-pedidos/` del
  Cliente.
- **Un nombre o una nota con HTML** se muestra como texto porque Angular
  escapa la interpolación: sin prueba propia.
- **Columna de búsqueda propia** en lugar de confiar en la intercalación de
  MySQL: así la búsqueda sin tildes se prueba en H2, como la del catálogo.
- **`estado` como `varchar`:** cada estado nuevo de `EstadoPedido` rompería
  el `enum` de MySQL igual que este. La tarea al arrancar es un cambio de
  tipo puntual, no una migración; se anota en `decisiones.md` → «Base de
  datos: sin migraciones».

*21/10/2026 (`/sdd:analizar`).* Anotada la columna `estado` en
`decisiones.md`, con su código; precisado RF-10; sumadas las pruebas de la
entrega con un vencimiento a la vez, del nombre guardado en la línea, de la
búsqueda vacía o larga, de la lista que no vence y del 401; carpetas
`despacho/`.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: interfaz (dos pantallas
nuevas con datos, con `break-ui`), `/code-review` y **`/security-review`**
(endpoints nuevos con permisos por rol que cambian un pedido).

## Resultado

*22/10/2026, `/sdd:cerrar HU-17`.* Las tres tareas, las revisiones y la
prueba manual del usuario con Docker y los cuatro roles («todas pasaron»).

### Cobertura RF → pruebas

| RF | Backend | Frontend |
|---|---|---|
| RF-1 | `sinFiltroListaLosPagadosElMasViejoPrimero`, `unEstadoOUnaPaginaQueNoSirvenMuestranLoMasCercano` | `lista los pedidos con quien retira, chip y enlace`, `sin pedidos lo dice`, `pide la lista solo con los filtros que filtran` |
| RF-2 | `sinFiltroLista…`, `conOtroEstadoOTodosElMasNuevoPrimero` | `de entrada filtra Pagado`, `cambiar el estado o buscar cambia la dirección` |
| RF-3 | `buscaPorNumeroOPorNombreSinTildesNiMayusculas`, `unaBusquedaVaciaNoFiltraYUnaLargaSeRecorta`, `PedidoTest.guardaElNombreDeQuienRetiraParaBuscarlo`, `BusquedaAlArrancarTest.completaLaBusquedaDeLosPedidosDeAntes` | `cambiar el estado o buscar…`, `un espacio de más en la búsqueda no cambia la página` |
| RF-4 | `laFichaTraeLasLineasElClienteYQuienEntrego` | `muestra los datos del pedido y del cliente`, `entregado dice quién y cuándo`, `lee la ficha, y nada sin id` |
| RF-5 | — (solo pantalla) | `Registrar entrega pide confirmar con nombre y teléfono`, `confirmar lo deja Entregado` |
| RF-6 | `entregarDejaElPedidoEntregadoConQuienYCuandoSinMoverElStock`, `entregarMientrasVenceOtroPedidoDelClienteNoSeTraba`, `PedidoTest.entregarSoloDesdePagado` | `confirmar lo deja Entregado`, `registra la entrega y devuelve la ficha` |
| RF-7 | `soloSeEntregaUnPedidoPagado`, `dosEntregasALaVezDejanUnaSola` | `con 409 se pone al día y lo avisa`, `un rechazo de la entrega trae el código` |
| RF-8 | `soloSeEntregaUnPedidoPagado` (ya entregado), `PedidoTest.entregarSoloDesdePagado` | `un entregado no ofrece nada` |
| RF-9 | `elContadorVeLaFichaPeroNoEntrega`, `sinSesion401YClienteRecibe403`, `elContadorVeLaListaYElClienteNo` | `el Contador no ve Registrar entrega`, `pedidos es del personal` |
| RF-10 | `elClienteVeLaFechaDeEntregaEnSuPedido` | `muestra Entregado con su fecha` (pedido), `muestra Entregado con su chip` (Mis pedidos) |
| RF-11 | — (solo pantalla) | `la tarjeta Pedidos para el personal y no para el Cliente`, `un empleado ve «Inicio», «Inventario» y «Pedidos»` |
| RF-12 | `unPedidoQueNoExisteResponde404`, `unEstadoOUnaPagina…` | `una dirección que no sirve muestra lo más cercano`, `si la página pedida no existe…`, `un pedido que no existe lleva a la lista` |

Técnico: `EstadoPedidoAlArrancarTest.enH2NoHaceNada`; en MySQL se comprobó con
Docker (la base local pasó de `enum` a `varchar(15)` y los 5 pedidos que había
recibieron el texto de búsqueda).

### Suites

- Backend: **596 pruebas, 0 fallas, 1 omitida** (antes 577).
- Frontend: **744 pruebas** (antes 719); `npx ng build` sin avisos.

### Revisiones

**Interfaz (subagente, 19 capturas):** 2 reales y 4 opcionales. Corregidos: el
foco del teclado sobre la lona no se veía en modo oscuro (amarillo sobre
claro; como «Mis pedidos», el acento pasa al color sobre lona); el botón
«Registrar entrega» corrido 8 px; el mensaje del 409 sonaba a error del
Empleado («otra persona lo entregó o cambió de estado»); en celular el botón
pasa debajo de «Quién lo retira». Descartados: el total largo en celular (no
desborda) y ocultar «Se retira en la tienda» en un pedido entregado del
Cliente (quién lo retiró sigue sirviendo).

**`/code-review` ronda 1 (10, 6 corregidos):** el cambio de la columna corría
con el servidor ya atendiendo (pasa a `SmartInitializingSingleton`: antes del
servidor y de las tareas, y si falla no arranca); el comentario de `estado`
prometía admitir estados nuevos y Hibernate crea un `check` en una base nueva
(corregido, también en `decisiones.md`); índices para la lista; el recorte
podía partir un emoji; la regla de los filtros repetida en el servicio y la
página (`consultaDeDespacho`); la caja «Entregado» del Cliente salía verde.
Descartados: la entrega sin revisar la cuenta del personal (el filtro JWT ya
rechaza una cuenta desactivada en cada petición); la paginación copiada en
tres servicios, la hora copiada en dos páginas y un 409 seguido de una caída
de red que tapa el aviso (pendientes, abajo).

**`/code-review` ronda 2 (el tope; 7, 3 corregidos):** los espacios de adelante
contaban para el límite de 100 (con 100 espacios y «ana», la lista salía sin
filtrar); un espacio de más al final volvía a la primera página; faltaba el
índice `(estado, confirmado)`. Pendientes los otros 4.

**`/security-review`:** sin hallazgos (los permisos de `/api/despacho/**`, la
búsqueda con LIKE, el `ALTER TABLE` y los datos expuestos, revisados).

### Prueba manual

*22/10/2026.* El usuario probó con Docker: el pago, la lista en «Pagado», la
búsqueda por número y sin tildes, la entrega con su confirmación, dos pestañas
a la vez (la segunda avisa), el Contador sin el botón, «Entregado» para el
Cliente y los filtros. **Todo pasó.**

### Distinto de lo planeado

- «Pedidos» también en el encabezado del personal, no solo en el panel (como
  el inventario; anotado en RF-11).
- `EstadoPedidoAlArrancar` corre al terminar de crear los beans
  (`SmartInitializingSingleton`), no con `ApplicationReadyEvent`.
- El texto se limpia de espacios antes del límite de 100, que cuenta
  caracteres completos.
- Índices `(estado, pagado)`, `(estado, confirmado)` y `confirmado`.
- El ayudante de la página de la dirección pasó a `controller/Paginas`, y los
  colores de los chips a `TONO_DE_ESTADO` en `core/modelos/pedido.ts`.

### Pendientes

- La paginación acotada («después de la última, la última») está copiada en
  `PedidoService`, `CatalogoService` y `DespachoService`.
- El límite de 100 caracteres solo está en esta búsqueda; las demás pasan por
  `Busquedas.patron` sin límite.
- `hora()` repetida en la página del pedido y en la ficha.
- Un 409 seguido de una caída de red al recargar la ficha tapa el aviso.
- La prueba del emoji no detectaría el error (H2 acepta el carácter partido),
  y ninguna prueba comprueba que el cambio de la columna corra antes del
  servidor (se comprobó con Docker).
- En una base nueva Hibernate crea un `check` de los estados; en una base
  convertida no hay `check` (anotado en `decisiones.md`).
