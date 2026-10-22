# Plan — HU-19 Exportar los reportes

Historia: `docs/historias/HU-19.md` · Issue #23 · Rama `feature/hu-19-exportar-reportes`

Primero el reporte completo, sin paginar, en `ReportesService`. Después los dos
armadores de archivo (Excel con Apache POI, PDF con OpenPDF), el endpoint
`GET /api/reportes/exportar` y, al final, los botones de exportar en
`/reportes`. Cada tarea se prueba sola.

## Decisiones técnicas

- **Dependencias nuevas en `backend/pom.xml`** (avisadas en la entrevista):
  `org.apache.poi:poi-ooxml:5.5.1` y `com.github.librepdf:openpdf:3.0.5`.
  POI 6.0.0 salió el 22/10/2026 y es demasiado nueva; la 5.5.1 es de noviembre
  de 2025. OpenPDF 3 usa el paquete `org.openpdf`. Las dos son de licencia
  libre (Apache 2.0, y LGPL/MPL).
- **Las mismas consultas que la pantalla.** Cada lista de `ReportesService`
  se divide en una versión completa (`List<…>`) y la paginada, que llama a
  `paginar(completa, pagina)`. Así el archivo y la pantalla no pueden dar
  cifras distintas (RF-6). Los clientes inactivos completos buscan todos los
  usuarios de la lista de una vez (`findAllById`), sin N+1.
- **`ReporteCompleto`** (registro del servicio) junta todo lo que se exporta.
  Los armadores reciben solo eso: no consultan la base ni conocen HTTP, y se
  prueban con datos armados a mano.
- **Todo en memoria (`byte[]`).** Con 366 días y unos miles de filas, el
  archivo pesa pocos cientos de kB. POI usa `XSSFWorkbook`, no el modo de
  flujo, que no hace falta.
- **El controlador valida el formato antes de consultar** y responde con
  `Content-Disposition: attachment` (el nombre lo arma `ContentDisposition`,
  que escapa el texto), `Cache-Control: no-store` (el archivo trae datos
  personales) y el tipo de contenido de cada formato.
- **Fórmulas en Excel:** las celdas de texto se escriben con
  `setCellValue(String)`, que POI guarda como texto y Excel no evalúa. La
  prueba lo verifica con un nombre `=1+1`.
- **Fuente del PDF:** Helvetica de OpenPDF (WinAnsi), que trae los acentos y
  la ñ. Un carácter que no tiene (un emoji) se omite sin romper el archivo, y
  la prueba lo cubre. No se incrusta una fuente para no sumar archivos.
- **Frontend:** `HttpClient` con `responseType: 'blob'` (es una acción, no
  una lectura con `httpResource`) y `shared/descarga/descargar-archivo.ts`,
  que ya existe. Un error llega como `Blob`: se lee su texto y se pasa a
  `comoErrorApi` para que diga lo mismo que el resto de la aplicación.

## Tarea 1 — El reporte completo · backend

**Archivos:** `service/ReportesService.java`,
`service/ReporteCompleto.java` (nuevo).

- `ReportesService`: `masVendidos(...)`, `menosVendidos(...)`,
  `sinVentaCompleto(...)`, `clientesInactivosCompletos(...)` y
  `perdidosCompletos(...)` devuelven las listas enteras; los métodos
  paginados actuales las usan con `paginar` (sin cambiar su respuesta).
- `ReporteCompleto(VentasResponse ventas, ResumenDePerdidos perdidos,
  List<ProductoVendido> masVendidos, List<ProductoVendido> menosVendidos,
  List<ProductoSinVenta> sinVenta, List<ProductoPerdido> productosPerdidos,
  List<ClienteInactivo> clientesInactivos, TipoCliente tipo, int dias,
  LocalDateTime generado, String autor)`.
- `ReportesService.completo(String desde, String hasta, TipoCliente tipo,
  String dias, Long idUsuario): ReporteCompleto`, todo en una sola
  transacción de lectura; el autor (nombre y apellido) lo busca el servicio
  con `UsuarioRepository`, y el controlador solo pasa el `idUsuario`.

**Pruebas** (`ReportesExportarIntegracionTest`, nuevo, sobre H2):
- `completoTraeLasListasSinPaginar`: 12 productos vendidos → 12 filas en más
  vendidos, contra 10 en la página (RF-7).
- `completoTieneLasMismasCifrasQueLaPantalla`: los totales y la primera
  página de cada lista coinciden con los endpoints de HU-18 (RF-6).
- `completoUsaLosDiasYElTipo`: con 60 días y un tipo, sin venderse y los
  clientes respetan el filtro (RF-2).
- Las pruebas de HU-18 siguen en verde sin cambios (la refactorización no
  cambia las páginas).

## Tarea 2 — El Excel · backend

**Archivos:** `pom.xml`, `service/exportar/ExportadorExcel.java` (nuevo).

- `ExportadorExcel.exportar(ReporteCompleto): byte[]`. Hojas: «Resumen»
  (encabezado de RF-5 y cifras con el período anterior y la diferencia),
  «Por fecha», «Por día de la semana», «Por hora», «Por categoría», «Por
  tipo de cliente», «Más vendidos», «Menos vendidos», «Sin venderse»,
  «Pedidos perdidos» (resumen y productos) y «Clientes inactivos» (aviso de
  RF-8 en la primera fila).
- Estilos: bolivianos `#,##0.00`, porcentaje `0.0 %` (el valor dividido por
  100), fechas `dd/mm/aaaa` y fecha y hora `dd/mm/aaaa hh:mm`; títulos en
  negrita, la fila de títulos fija (`createFreezePane`) y anchos de columna
  ajustados.
- Sin ventas: las hojas de ventas dicen «No hubo ventas en el período» (RF-9).
- Un margen nulo (sin costo) es el texto «sin costo»; el margen negativo
  queda negativo.

**Pruebas** (`ExportadorExcelTest`, unitaria, lee el archivo con POI):
- `tieneUnaHojaPorSeccion` (RF-10) y `elResumenTraeElEncabezado` (período,
  anterior, tipo, días, generado y autor) (RF-5).
- `losImportesSonNumerosConFormato`: tipo de celda `NUMERIC` y el formato de
  bolivianos (RF-10); `lasFechasSonFechas`: celda numérica con formato de
  fecha (RF-10).
- `unAnoVaPorMes`: 366 días → «Por fecha» por mes, 13 filas como máximo
  (caso límite).
- `acentosYEmojiVuelvenIguales`: «Llave ñandú 🔧» se lee igual (caso
  límite).
- `losClientesTraenContactoYAviso` (RF-8).
- `sinVentasLoDice` y `sinVenderseIgualSale` (RF-9).
- `unNombreConIgualQuedaComoTexto`: `=1+1` es `STRING`, no `FORMULA`
  (caso límite).
- `margenSinCostoYNegativo` (RF-10).

## Tarea 3 — El PDF · backend

**Archivos:** `service/exportar/ExportadorPdf.java` (nuevo).

- `ExportadorPdf.exportar(ReporteCompleto): byte[]`, en A4 vertical: título
  y encabezado (RF-5), cada sección con su título y una `PdfPTable` con
  `setHeaderRows(1)` (los títulos se repiten en cada página), y el pie
  «Página N de M» (con un evento de página y una plantilla para el total).
- Mismos textos de «sin ventas», «sin costo» y aviso de datos personales que
  el Excel.

**Pruebas** (`ExportadorPdfTest`, unitaria, lee el texto con el
`PdfTextExtractor` de OpenPDF):
- `empiezaConPdfYTraeElEncabezado` (RF-5, RF-11).
- `numeraLasPaginas`: con 60 productos, más de una página y «Página 2 de N»
  (RF-11).
- `repiteLosTitulosEnLaPaginaSiguiente` (RF-11).
- `traeLasSeccionesYElAviso` (RF-6, RF-8) y `sinVentasLoDice` (RF-9).
- `acentosYEmojiNoLoRompen`: «Llave ñandú 🔧» → el archivo se arma y dice
  «Llave ñandú» (caso límite).

## Tarea 4 — El endpoint · backend

**Archivos:** `controller/ReportesController.java`,
`service/exportar/FormatoExportacion.java` (nuevo: `XLSX`, `PDF`, con la
extensión y el tipo de contenido), `exception/FormatoInvalidoException.java`
(nuevo, 400 `FORMATO_INVALIDO`).

- `GET /api/reportes/exportar?formato=xlsx|pdf&desde&hasta&tipo&dias`, con
  `@AuthenticationPrincipal Long idUsuario`. El formato se lee sin importar
  mayúsculas; cualquier otro, o ninguno, es 400 (RF-3).
- Respuesta `200` con el archivo, `Content-Disposition: attachment;
  filename="reporte-ventas-AAAA-MM-DD-a-AAAA-MM-DD.xlsx"` y
  `Cache-Control: no-store` (RF-4).
- `SecurityConfig` ya cubre `/api/reportes/**` (RF-1).

**Pruebas** (en `ReportesExportarIntegracionTest`):
- `exportarExcelComoContador` y `exportarPdfComoAdministrador`: 200, tipo de
  contenido, nombre del archivo, `no-store` y el autor en el archivo (RF-1,
  RF-4, RF-5).
- `empleadoYClienteReciben403` y `sinSesionRecibe401` (RF-1).
- `formatoInvalidoEs400` (`doc`, vacío, ausente), `periodoInvalidoEs400` y
  `tipoODiasInvalidosEs400` (`tipo=zz`, `dias=7`, como en HU-18) (RF-3).
- La sesión vencida la resuelve el interceptor de errores del frontend, ya
  probado (caso límite, sin prueba propia).
- `sinPeriodoUsaEsteMes`: el nombre lleva del 1 a hoy (RF-2).

## Tarea 5 — Los botones · frontend

**Archivos:** `core/api/reportes.service.ts` (+ spec),
`core/api/mensajes-error.ts`, `features/reportes/reportes.page.{ts,html,scss}`
(+ spec).

- `ReportesService.exportar(filtros: FiltrosDeReportes, formato: 'xlsx' |
  'pdf'): Promise<void>`: pide el archivo con `HttpClient` (`responseType:
  'blob'`, `observe: 'response'`), toma el nombre de `Content-Disposition`
  (o `reporte-ventas.<ext>` si no viene) y llama a `descargarArchivo`. Si
  falla con un `Blob`, lo lee y rechaza con `comoErrorApi`.
- `mensajes-error.ts`: `FORMATO_INVALIDO` y `PERIODO_INVALIDO`, si falta.
- Pantalla: junto al título, un grupo «Exportar» con dos botones, «Excel» y
  «PDF» (Lucide `file-spreadsheet` y `file-text`), según `DESIGN.md`.
  Mientras se prepara, el botón dice «Preparando…» con `aria-busy` y los dos
  quedan desactivados; si falla, un aviso con `role="alert"` y los botones
  vuelven a servir (RF-12). Envía el período, el tipo y los días de la
  dirección, en cualquier sección (RF-2).

**Pruebas:**
- `reportes.service.spec.ts`: `exportar pide el formato con el período, el
  tipo y los días` (RF-2), `usa el nombre del encabezado` (RF-4) y
  `convierte el error en Blob en ErrorApi` (RF-12).
- `reportes.page.spec.ts`: `exporta con los filtros de la dirección desde
  cualquier sección` (RF-2), `muestra «Preparando…» y no pide dos veces`
  (RF-12, doble clic) y `muestra el error y deja reintentar` (RF-12).

## Revisiones (al cerrar)

Como en `docs/flujo-sdd.md`: la interfaz con un subagente (los botones en
escritorio y celular, modo claro y oscuro), `/code-review` y
`/security-review` (acceso, datos personales en el archivo, encabezados de
la descarga, fórmulas en Excel). Prueba manual con Docker: abrir el Excel y
el PDF y compararlos con la pantalla.

## Resultado

*22/10/2026.* Implementado en cinco tareas, con TDD. Los 12 RF tienen prueba
(comprobado contra el código).

### Cobertura RF → pruebas

| RF | Backend | Frontend |
|---|---|---|
| RF-1 | `exportarExcelComoContador`, `exportarPdfComoAdministrador`, `empleadoYClienteReciben403`, `sinSesionRecibe401` | — (la ruta ya era solo del Contador y el Administrador, HU-18) |
| RF-2 | `completoUsaLosDiasYElTipo`, `sinPeriodoUsaEsteMes` | `exporta con el formato, el período, el tipo y los días`, `exporta con los filtros de la dirección desde cualquier sección` |
| RF-3 | `formatoInvalidoEs400`, `periodoInvalidoEs400`, `tipoODiasInvalidosEs400` | — (solo backend) |
| RF-4 | `exportarExcelComoContador`, `exportarPdfComoAdministrador` | `exporta con el formato…` (el nombre del encabezado), `nombra el archivo si el backend no lo dice` |
| RF-5 | `exportarExcelComoContador`, `ExportadorExcelTest.elResumenTraeElEncabezado`, `ExportadorPdfTest.empiezaConPdfYTraeElEncabezado` | — (solo backend) |
| RF-6 | `completoTieneLasMismasCifrasQueLaPantalla`, `ExportadorPdfTest.traeLasSeccionesYElAviso` | — (solo backend) |
| RF-7 | `completoTraeLasListasSinPaginar`, `repiteLosTitulosEnLaPaginaSiguiente` | — (solo backend) |
| RF-8 | `losClientesTraenContactoYAviso`, `traeLasSeccionesYElAviso` | — (solo backend) |
| RF-9 | `ExportadorExcelTest.sinVentasLoDice`, `sinVenderseIgualSale`, `ExportadorPdfTest.sinVentasLoDice` | — (solo backend) |
| RF-10 | `tieneUnaHojaPorSeccion`, `losImportesSonNumerosConFormato`, `lasFechasSonFechas`, `margenSinCostoYNegativo` (y `unAnoVaPorMes`, `unNombreConIgualQuedaComoTexto`, `acentosYEmojiVuelvenIguales`) | — (solo backend) |
| RF-11 | `empiezaConPdfYTraeElEncabezado`, `numeraLasPaginas`, `repiteLosTitulosEnLaPaginaSiguiente` (y `acentosYEmojiNoLoRompen`) | — (solo backend) |
| RF-12 | — (solo frontend) | `convierte el error en Blob en ErrorApi`, `sin conexión rechaza con el error de conexión`, `muestra «Preparando…» y no pide dos veces`, `muestra el error y deja reintentar` |

### Suites

- Backend: **662 pruebas, 0 fallas, 1 omitida** (antes 634). Las 28 nuevas
  pasan también con `-Duser.timezone=UTC`, como en el CI.
- Frontend: **791 pruebas** en 67 archivos (antes 784); `npx ng build` sin
  avisos.

### Revisiones

**Interfaz (subagente, `playwright-cli`, escritorio y celular, claro y
oscuro):** 3 reales, corregidos: con `disabled` el foco se perdía mientras se
preparaba el archivo (pasa a `aria-disabled`); el botón cambiaba de ancho con
«Preparando…» (`min-width`); el botón ocupado se leía poco (opacidad
completa). Pendientes, de antes de esta historia (`_lista.scss`): el hover del
`.secundario` sin `@media (hover: hover)` queda pegado en el celular, y el
desactivado sin el borde punteado de `DESIGN.md`.

**`/security-review` (subagente):** sin hallazgos (acceso al endpoint,
`Cache-Control: no-store`, `Content-Disposition` armado con
`ContentDisposition`, las fórmulas quedan como texto, tamaño acotado por el
período de 366 días, dependencias).

**`/code-review` ronda 1 (10, 5 corregidos):** el reloj se leía varias veces
al armar el reporte (ahora una sola); los anchos de columna en un `int[16]`
fijo (ahora según las columnas de la hoja); los nombres de tipo y de unidad
en mapas (pasan a `switch`); el nombre de respaldo del frontend repetía el
formato del backend (pasa a `reporte-ventas.<ext>`); las pruebas no
restauraban `URL.createObjectURL`. Descartados: el PDF en dos pasadas para
«Página N de M» (lo planeado, probado y suficiente para el tamaño de la
tienda); el resumen con cifras en cero sin ventas (igual que la pantalla,
RF-6: el período anterior puede tener ventas); el catálogo leído dos veces
(cuesta poco); un `dias` inválido muestra «Revise los datos marcados» (la
pantalla corrige los días antes de pedir); el archivo en memoria (decisión
del plan).

**`/code-review` ronda 2 (el tope; 5, 4 corregidos):** `min-width` a 11em
para que entre «Preparando…»; restaurar `URL` borrando la propiedad si jsdom
no la traía; el bucle de anchos con `continue`; el formato de `imports` en
`reportes.page.ts`. Pendiente: `lineasPorProducto` corre dos veces en
`completo()` (más y menos vendidos).

### Prueba manual

*22/10/2026.* El usuario probó con Docker (MySQL) y todo pasó: Excel como
Contador con el tipo elegido (nombre del archivo, encabezado, cifras iguales
a la pantalla y sumables), PDF como Administrador («Página N de M», títulos
repetidos, aviso de datos personales), los días de otra sección, un período
sin ventas, el 403 como Empleado y el doble clic.

### Distinto de lo planeado

- `ReportesService.exportar` del frontend devuelve `{archivo, nombre}` y la
  descarga la hace la pantalla, en lugar de llamar a `descargarArchivo`.
- Entre el reporte y los armadores hay un modelo intermedio (`Celda`,
  `Seccion`, `Hoja`, en `SeccionesDelReporte`), que comparten el Excel y el
  PDF, y un servicio `ExportacionDeReportes` que elige el armador y el nombre.
- El nombre de respaldo es genérico (`reporte-ventas.<ext>`).
- Los botones usan `aria-disabled` y no `disabled` (revisión de interfaz).

### Pendientes

- La consulta de líneas por producto se repite en `completo()`.
- `_lista.scss`: hover pegado en el celular y desactivado sin borde punteado.
