# HU-18 (ampliación) — Clientes que compran

Historia: `docs/historias/HU-18.md` (RF-23 a RF-27). Decisiones:
`decisiones.md` → «Reportes» y «Exportar reportes» (18/11/2026). Origen:
`docs/lista-de-cambios.md` punto 4, opción B recortada que eligió Fabio. La
fila «En línea» del resumen es de #121 (HU-23 RF-35) y el total descontado,
de HU-24 (RF-19, RF-20): no entran acá. Los planes de HU-18 y HU-19 no se
tocan. Issue: **#127**. Rama: `feature/reportes-clientes`.

Plazo: entrega el viernes 20/11/2026. Estimado: **unas 5 horas** de TDD
(backend 2 h, exportación 45 min, frontend 1 h 30, cierre 45 min).

## Recorte

| De la propuesta (punto 4) | Queda |
|---|---|
| Compras y total gastado por cliente | **Entra** (RF-23) |
| Ticket promedio | **Entra** (RF-23): total ÷ compras, sin consulta extra |
| Primera y última compra | **Entra**, dentro del período (RF-23, P5) |
| Cada cuánto compra | **Entra** (RF-24): con 2+ compras, (última − primera) ÷ (compras − 1) es exactamente el promedio de los intervalos; sale de la misma consulta |
| Canal | **Entra** (RF-23): dos `count` más en la misma consulta |
| Ranking de mejores clientes por gasto | **Entra**: es el orden de la lista (RF-25) |
| Ranking por cantidad de compras | Fuera (P4): un conmutador más y su prueba, ~30 min |
| Lo que más compra (producto o categoría) | Fuera: una consulta por cliente × producto y elegir el máximo, ~1 h 30 |
| Clientes nuevos y cuántos volvieron | Fuera: necesita la primera compra de siempre (otra consulta) y una cifra nueva en pantalla, ~1 h |
| Ventas por categoría | Ya existe (HU-18 RF-14) |
| Productos por tipo de cliente | Ya existe (filtro de tipo, RF-5, sobre RF-11) |
| Ventas por hora del día | Ya existe (RF-10) |
| Ventas por Empleado en el mostrador | Fuera: nadie decidió quién lo ve ni para qué |
| Excel y PDF | **Entra** (RF-26): una hoja más con las piezas de HU-19 |

## Preguntas para Fabio

*19/11/2026:* Fabio aceptó todas las recomendaciones (P1 a P6); el issue es
el #127.

- **P1. Dónde va.** Recomendado: una sección nueva **«Clientes que compran»**
  (`seccion=compradores`) en la barra, antes de la de hoy, que pasa a
  llamarse **«Clientes inactivos»** (sigue `seccion=clientes`, los enlaces
  guardados no se rompen). La otra opción, las dos listas en «Clientes»,
  mezcla una lista con período y otra sin período bajo los mismos filtros.
  Afecta: RF-23, RF-27, tarea 5.
- **P2. Correo y teléfono en la lista.** Recomendado: **sí**, como en
  inactivos: los ven los mismos dos roles y sirven para atender o llamar al
  buen cliente; el Excel lleva el aviso de datos personales. Sin contacto,
  la lista queda más angosta y se quitan dos columnas. Afecta: RF-23, RF-26,
  tareas 1, 4 y 5.
- **P3. Cuentas desactivadas hoy (o que pasaron al personal).**
  Recomendado: **salen**, con «Cuenta desactivada»: sus compras del período
  fueron ventas y están en el resumen. La otra opción es mostrar solo cuentas
  activas, como inactivos. Afecta: RF-23, tareas 1 y 5.
- **P4. Orden.** Recomendado: **solo por gasto** (a igual gasto, más
  compras). El conmutador «por gasto / por compras» queda fuera por el plazo.
  Afecta: RF-25.
- **P5. Primera y última compra.** Recomendado: **dentro del período**, con
  los mismos filtros, para que todo en la fila hable del mismo período. «De
  siempre» serviría para marcar clientes nuevos, que queda fuera. Afecta:
  RF-23, RF-24.
- **P6. El issue.** La ampliación necesita su issue para el `Closes #N` del
  PR; Claude no crea issues sin pedido. Recomendado: que Fabio lo cree (o lo
  pida) con el título «HU-18 (ampliación) Clientes que compran».

Si Fabio responde distinto, se ajusta la historia y este plan antes de
programar; ninguna respuesta cambia la consulta, solo columnas, filtros o
el nombre de la sección.

## Decisiones de diseño

- **Un endpoint propio, `GET /api/reportes/compradores`** (`desde`, `hasta`,
  `tipo`, `canal`, `pagina`), como las otras listas. Queda bajo
  `/api/reportes/**`: el rol ya lo valida `SecurityConfig` (RF-1), sin
  tocarlo.
- **Una sola consulta agrupada por cuenta** en `ReportesRepository`, con
  `join p.usuario u` (no `left join`): el consumidor final queda fuera solo.
  Compras, total, primera y última y los dos canales salen de ahí; el
  promedio y los días se calculan en Java, como el resto de los reportes
  (H2 y MySQL iguales). Sin filtrar por `rol` ni por `estado` de la cuenta
  (P3): la lista cuadra con el resumen menos el consumidor final.
- **Cada cuánto, en días de calendario con un decimal:** `ChronoUnit.DAYS`
  entre las fechas (`toLocalDate`, ya en hora de Bolivia) ÷ (compras − 1),
  `HALF_UP`; `null` con una compra. **Motivo:** el Contador piensa en días,
  no en horas, y dos compras el mismo día dan 0 («El mismo día»).
- **El canal viaja como dos números** (`enLinea`, `enTienda`) y la pantalla
  y el archivo dicen «En línea», «En tienda» o «Los dos». **Motivo:** no hay
  un enum nuevo que se cruce con el `MedioDelResumen` de #121.
- **Orden en Java:** total desc, compras desc, `idUsuario` asc (para que la
  página sea estable). Se pagina con `ReportesService.paginar`, como las
  demás.
- **Las cuentas se leen de una vez** (`usuarioRepository.findAllById`) solo
  para la página, como `clientes()` de inactivos; en `completo()`, para
  todas.
- **Pruebas de integración en una clase nueva,**
  `ReportesCompradoresIntegracionTest`, con su propio `@BeforeEach`.
  **Motivo:** #121 y HU-24 amplían `ReportesIntegracionTest` y
  `ReportesExportarIntegracionTest`; una clase nueva no choca con ninguno.
  Usa la misma configuración que las otras, así que reutiliza el contexto de
  Spring en caché.
- **Sin cambios en `docs/lista-de-cambios.md` hasta el cierre:** #121 también
  lo toca en su cierre (§ 4).

## Tareas

Orden: 1 → 2 → 3 → 4 (backend; `./mvnw test` completo al terminar la 3,
porque cambia `ReporteCompleto`) → 5 → 6 (frontend; la 5 toca `core/`:
`npx ng test --watch=false` completo al terminarla) → 7. **Empezar después
de mergear #121** y rebasar (ver «Conflictos»).

### 1. La consulta y el DTO (RF-23, RF-24, RF-25)

- `dto/reportes/CompradorFila(Long idUsuario, Long compras, BigDecimal total,
  LocalDateTime primera, LocalDateTime ultima, Long enLinea, Long enTienda)`.
- `dto/reportes/ClienteQueCompra(Long idUsuario, String nombre, String
  apellido, String correo, String telefono, TipoCliente tipo, boolean activa,
  long compras, BigDecimal total, BigDecimal promedio, LocalDateTime
  primera, LocalDateTime ultima, BigDecimal diasEntreCompras, long enLinea,
  long enTienda)`; `diasEntreCompras` nulo con una compra. Con P2 en «no»,
  sin `correo` ni `telefono`.
- `ReportesRepository.compradores(estados, inicio, fin, tipo, canal)`.
  **Ponerla después de `lineasPorProducto`,** no al final del archivo
  (ahí cambian #121 y HU-24):

  ```java
  // HU-18 RF-23: lo que compró cada cuenta en el período; el consumidor final no tiene cuenta y queda fuera del join
  @Query("""
  		select new bo.edu.uagrm.tienda.dto.reportes.CompradorFila(u.idUsuario, count(p), sum(p.total),
  			min(p.pagado), max(p.pagado),
  			count(case when p.canal = bo.edu.uagrm.tienda.entity.Canal.EN_LINEA then 1 end),
  			count(case when p.canal = bo.edu.uagrm.tienda.entity.Canal.TIENDA then 1 end))
  		from Pedido p join p.usuario u
  		where p.estado in :estados and p.pagado >= :inicio and p.pagado < :fin
  			and (:tipo is null or u.tipoCliente = :tipo) and (:canal is null or p.canal = :canal)
  		group by u.idUsuario""")
  ```
- Pruebas, primero (`ReportesCompradoresIntegracionTest`, nueva; MockMvc
  como Contador sobre `/api/reportes/compradores`; las ventas del mostrador
  con `Pedido.enTienda` como `ReportesIntegracionTest.enTienda`):
  - `compradoresConSusCifras` — el escenario «Los mejores clientes del mes»:
    Beto primero (1, 800, sin días, solo tienda), Ana (3, 600, promedio 200,
    01/10 y 21/10, 10.0 días, solo en línea) (RF-23, RF-24, RF-25).
  - `elConsumidorFinalNoEntra` — su venta suma en `/ventas` pero no en la
    lista (RF-23).
  - `soloCuentanLasVentasDelPeriodo` — «Pago a devolver», «Anulado»,
    «Vencido», «Cancelado» y pendiente no cuentan; un pago a las 23:59 del
    último día sí, a las 00:00 del siguiente no; un pedido confirmado en
    septiembre y pagado en octubre cuenta en octubre (RF-23, HU-18 RF-4).
  - `unaSolaCompraYElMismoDia` — `diasEntreCompras` nulo con una; `0.0` con
    dos el mismo día (RF-24).
  - `ordenaPorTotalYAIgualTotalPorCompras` (RF-25).
  - `filtraPorTipoYPorCanal` — Ana con una compra en línea y otra en el
    mostrador: con `canal=TIENDA`, 1 compra, `enLinea` 0; con
    `tipo=CONTRATISTA`, solo los contratistas (RF-25).
  - `laListaCuadraConElResumen` — suma de `total` = `actual.ingresos` de
    `/ventas` menos lo vendido al consumidor final (RF-23).
  - `laCuentaDesactivadaSigueSaliendo` — `activa` falso (RF-23, P3).

### 2. El servicio y el controlador (RF-23 a RF-25; HU-18 RF-1, RF-3)

- `ReportesService`:
  - `public PaginaResponse<ClienteQueCompra> compradores(String desde, String
    hasta, TipoCliente tipo, Canal canal, int pagina)`, `@Transactional(readOnly
    = true)`.
  - `private List<CompradorFila> compradores(Periodo, TipoCliente, Canal)`,
    ordenada; `private List<ClienteQueCompra> conCuenta(List<CompradorFila>)`
    (lee las cuentas de una vez); `static BigDecimal cadaCuanto(CompradorFila)`.
  - Ponerlos **después de `clientesInactivos`/`clientes`**, lejos de
    `completo()`, `productos()` (#121 tarea 5) y `totales()` (HU-24).
- `ReportesController`: `@GetMapping("/compradores")` con los mismos
  parámetros de texto que `/productos` y `Paginas.numeroDePagina(pagina)`.
- Pruebas, primero (misma clase):
  - `paginaDeA10` — 11 cuentas: página 1 con una; `pagina=-1` y `pagina=zz`
    dan la 0; `pagina=99`, la última (RF-25).
  - `periodoInvalidoEs400` — `desde=xx` → 400 `PERIODO_INVALIDO` (HU-18 RF-3).
  - `soloContadorYAdministrador` — Empleado y Cliente 403, sin sesión 401,
    Administrador 200 (HU-18 RF-1).
  - `tipoBasuraEs400YCanalBasuraSonLosDos` — `tipo=zz` → 400, como las otras
    listas; `canal=zz` → los dos canales, porque `Canal.deTexto` devuelve
    `null` a lo que no conoce (RF-25).

### 3. Al reporte completo (RF-26)

- `ReporteCompleto` suma `List<ClienteQueCompra> clientesQueCompran`, antes
  de `clientesInactivos`. `ReportesService.completo()` lo llena con
  `conCuenta(compradores(periodo, tipo, canal))`, sin paginar. Ajustar los
  tres `new ReporteCompleto` de `ReporteDePrueba` (dos filas de ejemplo en
  el reporte base, lista vacía en el sin ventas).
- Prueba, primero (`ReportesCompradoresIntegracionTest`):
  - `completoTraeTodosLosQueCompranSinPaginar` — 12 cuentas, las 12, en el
    mismo orden que la pantalla (RF-26).
- Suite completa del backend (cambió un record compartido).

### 4. La hoja y la sección del PDF (RF-26)

- `SeccionesDelReporte.de`: `hojas.add(hoja("Clientes que compran",
  compradores(reporte.clientesQueCompran(), sinVentasTexto)))` antes de
  «Clientes inactivos». Columnas: Cliente, Correo, Teléfono, Tipo, Canal,
  Compras, Total, Promedio, Primera compra, Última compra, Cada cuántos
  días. Notas: `AVISO_DATOS_PERSONALES` (con P2 en «sí») y «Cuenta
  desactivada» en la columna Cliente cuando `!activa`. Días:
  `new Numero(valor, 1)`, o texto «Una sola compra». Vacío: «Ningún cliente
  con cuenta compró en este período.»
- Pruebas, primero:
  - `ExportadorExcelTest.tieneUnaHojaPorSeccion` — una hoja más, con su
    nombre en orden (RF-26).
  - `ExportadorExcelTest.losClientesQueCompranTraenSusCifras` — total,
    promedio y días como números, el aviso y «Una sola compra» (RF-26).
  - `ExportadorPdfTest.traeLasSeccionesYElAviso` — el título nuevo (RF-26).

### 5. Frontend: modelo, servicio y sección (RF-23 a RF-25, RF-27)

- `core/modelos/reportes.ts`: `Seccion` suma `'compradores'`; `SECCIONES`:
  `{ valor: 'compradores', nombre: 'Clientes que compran' }` antes de
  `clientes`, y `clientes` pasa a `'Clientes inactivos'` (P1). `interface
  ClienteQueCompra` espejo del DTO. **Agregar la interfaz después de
  `ClienteInactivo`,** lejos de `Totales` (HU-24) y del medio del resumen
  (#121).
- `core/api/reportes.service.ts`: `compradores(filtros: Signal<FiltrosDeReportes>):
  HttpResourceRef<Pagina<ClienteQueCompra> | undefined>` con período, tipo,
  canal y página.
- `features/reportes/reportes-compradores.ts`, `.html`, `.scss` (nueva,
  como `reportes-clientes`): tabla con Cliente (nombre, tipo y «Cuenta
  desactivada»), Contacto (P2), Compras (con el canal debajo), Total (con el
  promedio debajo), Primera y última, Cada cuánto («Cada 10 días», «El mismo
  día», «Una sola compra»). Nota fija: «Solo clientes con cuenta: las
  ventas al consumidor final no aparecen acá.» Estados de RF-22 con
  `ultimoValor` y `errorDeLectura`. Diseño con `DESIGN.md` y las tablas de
  `_lista.scss` (celda con `data-etiqueta` en el celular).
- `reportes.page.html`: `@case ('compradores')`. `usaPeriodo` y `usaTipo` no
  cambian (la sección usa los dos).
- `reportes-falso.spec-helper.ts`: `compradoresRecurso`.
- Pruebas, primero:
  - `core/modelos/reportes.spec.ts`: `RF-27: lee y escribe
    seccion=compradores`.
  - `core/api/reportes.service.spec.ts`: `RF-25: pide los compradores con el
    período, el tipo, el canal y la página`.
  - `reportes-listas.spec.ts`: `RF-23, RF-24: clientes que compran` (fila
    completa, enlaces `mailto:` y `tel:`, «Cada 10 días», «En línea»);
    `RF-24: una sola compra y el mismo día`; `RF-23: cuenta desactivada`;
    `RF-22: sin compras lo dice` (en «sin datos lo dice cada lista»).
  - `reportes.page.spec.ts`: `RF-27: la barra dice «Clientes que compran» y
    «Clientes inactivos»`; `RF-25: «Clientes que compran» muestra período,
    tipo y canal` (en «cada sección muestra solo los filtros que usa»).
- Suite completa del frontend (tocó `core/`).

### 6. Datos peores (sin RF nuevo)

- `break-ui` sobre `reportes-compradores` con nombres y correos largos, 99
  compras, Bs 99 999 999,99 y un decimal largo de días: que no rompa la
  tabla en el celular. Lo que encuentre, con su prueba si es lógica.

### 7. Cierre

- `/sdd:cerrar HU-18`: cobertura RF-23 a RF-27, suites completas, revisión
  de interfaz (escritorio y celular, modo claro y oscuro, con datos y sin
  datos), `/code-review` y `/security-review` (datos personales de clientes
  en un endpoint y en el archivo exportado). Prueba manual con Docker como
  Contador y Administrador, y el 403 como Empleado.
- `docs/lista-de-cambios.md` § 4: lo que se hizo y lo que quedó fuera.
- `docs/proyecto/requisitos.md` con `/proyecto:requisitos` si Fabio lo pide.

## Cobertura de los RF

| RF | Pruebas |
|---|---|
| RF-23 | `compradoresConSusCifras`, `elConsumidorFinalNoEntra`, `soloCuentanLasVentasDelPeriodo`, `laListaCuadraConElResumen`, `laCuentaDesactivadaSigueSaliendo`; front «clientes que compran», «cuenta desactivada» |
| RF-24 | `compradoresConSusCifras`, `unaSolaCompraYElMismoDia`; front «una sola compra y el mismo día» |
| RF-25 | `ordenaPorTotalYAIgualTotalPorCompras`, `filtraPorTipoYPorCanal`, `paginaDeA10`, `tipoBasuraEs400YCanalBasuraSonLosDos`; front servicio y filtros de la página |
| RF-26 | `completoTraeTodosLosQueCompranSinPaginar`, `tieneUnaHojaPorSeccion`, `losClientesQueCompranTraenSusCifras`, `traeLasSeccionesYElAviso` |
| RF-27 | front «la barra dice…», «lee y escribe seccion=compradores» |
| HU-18 RF-1, RF-3, RF-22 | `soloContadorYAdministrador`, `periodoInvalidoEs400`; front «sin compras lo dice» |

## Conflictos con #121 y HU-24

Las tres ramas tocan los reportes. Lo que choca, por archivo:

| Archivo | #121 (`fix/auditoria-18-11`) | HU-24 PR 3 | Esta rama | Choque |
|---|---|---|---|---|
| `ReportesRepository.java` | cambia `comprasHasta` (al final) | agrega `descuentos` (seguramente al final) | agrega `compradores` después de `lineasPorProducto` | Ninguno si se respeta el lugar |
| `ReportesService.java` | `productos`/`completo` (tarea 5), `porMedio` | `totales` | métodos nuevos tras `clientes()` y **una línea en `completo()`** | **Sí con #121 en `completo()`**: se resuelve a mano en 2 min |
| `ReporteCompleto.java` | — | — | un componente más | No |
| `SeccionesDelReporte.java` | quizás «Por medio» | `cifras()` | una hoja nueva en `de()` | Bajo: líneas distintas |
| `ReporteDePrueba.java` (prueba) | — | quizás `Totales` nuevos | los tres `new ReporteCompleto` | Bajo con HU-24 |
| `ExportadorExcelTest`, `ExportadorPdfTest` | — | quizás | pruebas nuevas al final | Bajo (dos agregados al final se juntan a mano) |
| `ReportesIntegracionTest`, `ReportesExportarIntegracionTest` | pruebas nuevas | pruebas nuevas | **no se tocan** (clase nueva) | No |
| `core/modelos/reportes.ts` | `MedioDelResumen` (~línea 197) | `Totales` | `Seccion`, `SECCIONES`, interfaz tras `ClienteInactivo` | Bajo |
| `reportes.page.*`, `reportes-falso.spec-helper.ts`, `reportes-listas.spec.ts`, `reportes.service.*` | — | — | sí | No |
| `docs/historias/HU-18.md` | nota en RF-16 | nota en «Fuera de alcance» (Descuentos) | Contexto, RF-23 a 27, escenarios, casos límite, fin de «Fuera de alcance», «Dudas abiertas» | No: hunks separados |
| `docs/decisiones.md` | bullets al final de «Reportes», «Pagos», «Venta en tienda» | índice, «Fuera» de «Reportes», «Descuentos» nueva | bullet tras «Pedidos perdidos» y otro en «Exportar reportes»; **sin tocar el índice** | No: hunks separados |
| `docs/lista-de-cambios.md` | § 4 en su cierre | — | § 4 en el cierre | **Sí**: el segundo en cerrar lo junta |

**Orden recomendado:** **#121 primero** (arreglos de dinero y bloqueos ya
planificados, y cambia `completo()` y `comprasHasta`), **después esta
rama**, rebasada sobre `main` antes de programar la tarea 3, y **HU-24 PR 3
al final** (es lo primero que su plan recorta si falta tiempo; su `totales()`
no se cruza con esta rama). Si HU-24 PR 3 llega antes, esta rama rebasa y
solo junta `ReporteDePrueba` y, quizás, los finales de los archivos de
prueba del exportador.

## Análisis antes de programar (como `/sdd:analizar`, 18/11/2026)

Revisados la ampliación de HU-18, este plan, `CLAUDE.md`, `docs/flujo-sdd.md`,
`decisiones.md` («Reportes», «Exportar reportes», «Venta en tienda»), HU-19,
HU-23 (RF-21, RF-22), el plan de #121 (RF-35, RF-36) y HU-24 (RF-19, RF-20).

- **Variante del flujo:** cambio a una historia cerrada → ampliación de HU-18,
  RF desde RF-23 (el último era RF-22; #121 y HU-24 no suman RF a HU-18, solo
  notas). Decisión fechada en `decisiones.md`; plan nuevo; los planes viejos
  no se tocan. Coherente.
- **Regla de venta:** la consulta usa `VENDIDOS` y `pagado` en el período,
  como `pagados`: coincide con RF-4 y con «Reportes». La prueba
  `laListaCuadraConElResumen` lo ata al resumen.
- **Acceso (RF-1):** `SecurityConfig` ya cubre `/api/reportes/**` para
  Contador y Administrador; verificado en el código (línea 77). La pantalla ya
  está detrás del mismo control. Sin cambios de seguridad; igual se prueba
  el 403 del endpoint nuevo.
- **Canal:** «Clientes que compran» filtra por canal como el resumen; D3 de
  #121 (inactivos miran todos los canales) **no se contradice**: son
  preguntas distintas, y la historia lo dice en «Casos límite».
- **Consumidor final:** fuera por el `join`; coherente con HU-23 y con la
  lista de cambios. Por eso la lista suma menos que el resumen; la pantalla
  lo dice.
- **Datos personales:** correo y teléfono (P2) siguen la misma regla que
  inactivos y el mismo aviso en el archivo (HU-19 RF-8). Ningún correo
  nuevo: `CLAUDE.md` → «Correos» no cambia.
- **HU-19 RF-6 y RF-7** nombran «todas las secciones de HU-18» y las listas
  sin paginar: la hoja nueva entra en esa regla; RF-26 lo hace explícito.
  HU-19 no se edita.
- **Hibernate:** `count(case … then 1 end)` da `Long` en H2 y MySQL (cuenta
  los no nulos); `sum(case …)` podía dar `Integer` o `Long` según el
  dialecto. Por eso `count`.
- **Hallazgo menor (corregido en el plan):** la rama no sigue el nombre
  `feature/hu-XX-…` de `CLAUDE.md`. Se puede dejar (ya existe) o renombrar a
  `feature/hu-18-clientes-que-compran` antes del primer push; decide Fabio.
- **Sin hallazgos críticos.** Lo que bloquea empezar: P1 a P6 (sobre todo
  P6, el issue) y mergear #121.

## Resultado

*19/11/2026.* Tareas 1 a 5 con TDD por Claude (Opus); las revisiones, de
Fable. Fabio aceptó P1 a P6 (issue #127).

**Suites:** backend **937 pruebas**, 0 fallas; frontend **984 pruebas**,
`ng lint` y `ng build` sin avisos.

**Cobertura:** la de la tabla de arriba, con estas diferencias:
`elConsumidorFinalNoEntra` va dentro de `compradoresConSusCifras`; «lee y
escribe seccion=compradores» lo cubre `reportes.page.spec.ts` («cada sección
muestra solo los filtros que usa», que abre `seccion=compradores`), y la
barra, «muestra las secciones y cambia la dirección».

**Distinto de lo planeado:**

- Se programó antes del merge de #121, en una clase de pruebas propia, como
  preveía el plan para no chocar; la rama se mezcla con `main` después.
- La pantalla junta «Contacto» y «Primera y última»; el Excel y el PDF los
  separan en columnas (Correo, Teléfono, Primera compra, Última compra) y
  suman «Canal», para poder ordenar y filtrar en la planilla. Los datos son
  los mismos (RF-26).
- La tarea 6 (`break-ui`) va con la revisión de interfaz.

**Revisiones:**

- **`/code-review` y `/security-review` (un subagente Fable):** nada
  importante. `/api/reportes/compradores` queda bajo la regla de
  `/api/reportes/**` (Contador y Administrador); `mailto:` y `tel:` con
  binding de Angular. Corregido el menor: con 0 días, el Excel y el PDF
  decían «0,0» y ahora dicen «El mismo día», como la pantalla (con su
  prueba).
- **Interfaz (pendiente):** se hace junto con la de #121, con `main` ya
  mezclado en Docker.

**Prueba manual:** pendiente. Fabio la hace al final, junto con las demás.
