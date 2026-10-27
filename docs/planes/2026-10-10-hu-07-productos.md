# Plan — HU-07 Productos

Historia: `docs/historias/HU-07.md` · Issue #10 · Rama `feature/hu-07-productos`

Historia nueva, con el cierre de lo pendiente de HU-06 (RF-10 y el conteo de
productos). Backend: entidad, código, ficha, imágenes y API de gestión.
Frontend: lista con filtros y un formulario de alta y edición.

## Tarea 1 — Entidad y repositorio · backend

**Archivos:** `entity/UnidadVenta.java`, `entity/Caracteristica.java`
(`@Embeddable`), `entity/Producto.java`, `repository/ProductoRepository.java`.

- `UnidadVenta`: UNIDAD, METRO, KILO, LITRO, CAJA, PAR, ROLLO.
- `Producto`: `idProducto` (`Long`), `codigo` (20, único, se asigna después
  del primer guardado), `nombre` (120), `marca` (60, nula), `descripcion`
  (1000, nula), `precio` (`BigDecimal`, `DECIMAL(7,2)`), `unidad` (enum como
  texto), `categoria` (`@ManyToOne(LAZY)`, obligatoria), `activo`,
  `fechaAlta`; `ficha` (`@ElementCollection` de `Caracteristica`, con
  `@OrderColumn`, tabla `producto_caracteristica`) e `imagenes`
  (`@ElementCollection` de nombres de archivo, `@OrderColumn`, tabla
  `producto_imagen`).
- `ProductoRepository`: `buscar(idCategoria, activo, texto, Pageable)` (JPQL,
  como `UsuarioRepository.buscar`), `existsByCategoria_IdCategoria`,
  `contarPorCategoria()` (`idCategoria` y total, para la gestión de
  categorías), `findConBloqueoByIdProducto`.

**Pruebas** (`ProductoRepositoryTest`, con `flush`/`clear`):
- `guardaYLeeTodasLasColumnasConFichaEImagenesEnOrden` — HU-07 RF-2, RF-6, RF-8
- `codigoRepetidoSeRechaza` — HU-07 RF-4
- `buscarCombinaCategoriaEstadoYTextoEnNombreCodigoYMarca` — HU-07 RF-10
- `contarPorCategoriaCuentaActivosEInactivos` — HU-07 RF-13

## Tarea 2 — Servicio · backend

**Archivos:** `service/ProductoService.java`, `dto/ProductoRequest.java`,
`dto/CaracteristicaRequest.java`, `dto/ProductoResponse.java`,
`dto/ProductoResumenResponse.java`, `dto/OrdenImagenesRequest.java`,
excepciones `ProductoNoEncontradoException` (404 `PRODUCTO_NO_ENCONTRADO`),
`ProductoLlenoDeImagenesException` (409 `PRODUCTO_LLENO_DE_IMAGENES`),
`FichaInvalidaException` (400 `FICHA_INVALIDA`),
`OrdenDeImagenesInvalidoException` (400 `ORDEN_DE_IMAGENES_INVALIDO`),
`CategoriaConProductosException` (409 `CATEGORIA_CON_PRODUCTOS`).

- `ProductoRequest`: `@NotBlank @Size(max=120)` nombre, `@Size(max=60)` marca,
  `@Size(max=1000)` descripción, `@NotNull` idCategoria y unidad, precio
  `@NotNull @DecimalMin(value="0", inclusive=false) @DecimalMax("99999.99")
  @Digits(integer=5, fraction=2)`, `@Size(max=15)` ficha con `@Valid`
  `CaracteristicaRequest(@NotBlank @Size(max=40) nombre, @NotBlank @Size(max=100) valor)`.
  Recorta en el constructor y descarta los pares vacíos.
- `crear`: categoría existente; ficha sin nombres repetidos (sin importar
  mayúsculas); `saveAndFlush` y después `asignarCodigo(prefijo + "-" +
  "%04d".formatted(id))`.
- `editar`, `cambiarActivo`.
- Imágenes (con la fila bloqueada, como en HU-06):
  `agregarImagen(id, bytes)` (formato por contenido, máximo 5, archivo nuevo
  borrado si se revierte), `quitarImagen(id, nombre)` (archivo borrado después
  del commit), `reordenar(id, nombres)` (tiene que ser una permutación exacta).
- `CategoriaService.borrar`: si `existsByCategoria_IdCategoria`, 409
  `CATEGORIA_CON_PRODUCTOS`.

**Pruebas** (`ProductoServiceTest` y `CategoriaServiceTest`):
- `creaActivoConCodigoDeLaCategoria` — HU-07 RF-2, RF-4
- `prefijoSinTildesYEnMayusculas` («Jardín» → «JAR») — HU-07 RF-4
- `categoriaInexistenteAlCrear` — HU-07 RF-11
- `fichaConNombresRepetidosSeRechaza`, `paresVaciosSeDescartan` — HU-07 RF-6
- `editarCambiaLaCategoriaSinCambiarElCodigo` — HU-07 RF-5
- `desactivarYActivar` — HU-07 RF-9
- `sextaImagenSeRechaza`, `imagenInvalidaSeRechaza` — HU-07 RF-7
- `reordenarConOtraListaSeRechaza` — HU-07 RF-8
- `CategoriaServiceTest.categoriaConProductosNoSeBorra` — HU-07 RF-12, HU-06 RF-10

## Tarea 3 — API · backend

**Archivos:** `controller/GestionProductosController.java`,
`controller/ProductosController.java` (solo la imagen pública),
`controller/GestionCategoriasController.java` (conteo), `config/SecurityConfig.java`.

| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/gestion/productos?categoria=&activo=&texto=&pagina=` | Administrador |
| GET | `/api/gestion/productos/{id}` | Administrador |
| POST | `/api/gestion/productos` | Administrador |
| PUT | `/api/gestion/productos/{id}` | Administrador |
| PUT | `/api/gestion/productos/{id}/activo` | Administrador |
| POST | `/api/gestion/productos/{id}/imagenes` (multipart `imagen`) | Administrador |
| PUT | `/api/gestion/productos/{id}/imagenes` (`{ imagenes: [...] }`) | Administrador |
| DELETE | `/api/gestion/productos/{id}/imagenes/{nombre}` | Administrador |
| GET | `/api/productos/{id}/imagenes/{nombre}` | público |

`SecurityConfig`: `GET /api/productos/**` público (lo usará el catálogo).

**Pruebas** (`ProductosIntegracionTest`): permisos, alta con código, precio y
ficha inválidos, editar, desactivar, imágenes (subir, sexta, reordenar,
quitar, servir), filtros y paginación, y la categoría con productos que no se
borra y muestra su conteo — HU-07 RF-1 a RF-13, HU-06 RF-10 y RF-12.

## Tarea 4 — Frontend

**Archivos:** `core/modelos/producto.ts`, `core/api/productos.service.ts`,
`features/productos/productos.page.*` (lista), `features/producto/producto.page.*`
(formulario de alta y edición, con las imágenes y la ficha), rutas
`/productos`, `/productos/nuevo` y `/productos/:id` con `rolGuard`, menú y
panel del Administrador; `categorias.page` maneja `CATEGORIA_CON_PRODUCTOS`
ofreciendo ocultar.

- Lista: `httpResource` con filtros en la URL (como «Usuarios»), tabla con
  imagen principal, código, nombre, categoría, precio («Bs 420,00» con `Intl`),
  estado, y activar/desactivar.
- Formulario: Signal Forms (nombre, marca, categoría de un `select` con las
  categorías de gestión, unidad, precio que acepta coma o punto, descripción) y
  la ficha como filas que se agregan y quitan. Con el producto ya creado:
  imágenes (subir, mover a principal, quitar).
- Mensajes nuevos en `mensajes-error.ts`.

**Pruebas:** servicio (crear, editar, imágenes, filtros), lista (filtros, estado,
paginación, precio con formato), formulario (validaciones, precio con coma,
ficha, imágenes, sexta rechazada), categorías («borrar con productos ofrece
ocultar»), rutas y menú — HU-07 RF-1 a RF-12.

## Decisiones

- El código se asigna en la misma transacción, después del primer guardado:
  necesita el número del producto, y así nunca hay dos iguales.
- La ficha y las imágenes son colecciones de la entidad (`@ElementCollection`
  con orden), no entidades propias: no tienen vida fuera del producto.
- Las imágenes se identifican por el nombre de archivo que genera el sistema;
  el servicio solo acepta nombres que ya son del producto.
- Formulario en su propia ruta (`/productos/nuevo`, `/productos/:id`) y no en
  la lista: tiene muchos campos y las imágenes.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: las tres (subida de
archivos, dinero —precios—, endpoint público y frontend).

## Resultado

*10/10/2026, `/sdd:cerrar HU-07`.*

### Cobertura RF → pruebas

| RF | Pruebas |
|---|---|
| RF-1 | `ProductosIntegracionTest.sinSesionLaGestionDevuelve401YUnClienteRecibe403` (alta, foto y estado); `app.routes.spec.ts` «la gestión de productos es solo del administrador»; `app.component.spec.ts` (menú); `panel.page.spec.ts` (acceso) |
| RF-2 | `ProductoRepositoryTest.guardaYLeeTodasLasColumnasConFichaEImagenesEnOrden`; `ProductoServiceTest.creaActivoConCodigoDeLaCategoria`; `ProductosIntegracionTest.creaConCodigoYFichaYApareceEnLaLista`; front: «crea y pasa a la página del producto con su código», servicio «crea con los datos limpios» |
| RF-3 | `ProductosIntegracionTest.precioFueraDeRangoOConTresDecimalesSeRechaza`, `nombreYUnidadInvalidosSeRechazan`; front: «marca lo que falta y no envía», «rechaza un precio de cero o con tres decimales», «marca el campo que rechazó el backend», `precios.spec.ts` (`leerPrecio`, `problemaDelPrecio`) |
| RF-4 | `ProductoRepositoryTest.codigoRepetidoSeRechaza`; `ProductoServiceTest.creaActivoConCodigoDeLaCategoria`, `prefijoSinTildesYEnMayusculas`, `prefijoSoloConLetras` |
| RF-5 | `ProductoServiceTest.editarCambiaLaCategoriaSinCambiarElCodigo`; `ProductosIntegracionTest.editarLoPasaDeCategoriaSinCambiarElCodigo`; front: «carga el producto y guarda los cambios» |
| RF-6 | `ProductoServiceTest.fichaConNombresRepetidosSeRechaza`, `paresVaciosSeDescartan`; `ProductosIntegracionTest.fichaInvalidaSeRechaza`; front: «agrega y quita datos de la ficha», «marca un dato sin valor y los nombres repetidos», «no deja agregar más de 15 datos», «después de guardar muestra lo guardado, sin el par vacío» |
| RF-7 | `ProductoServiceTest.imagenSeAgregaAlFinal`, `sextaImagenSeRechaza`, `imagenInvalidaSeRechaza`; `ProductosIntegracionTest.sextaImagenYArchivoQueNoEsImagenSeRechazan`; front: «sube la foto elegida», «con cinco fotos no ofrece subir otra», «explica una foto rechazada» |
| RF-8 | `ProductoServiceTest.reordenarPoneLaElegidaComoPrincipal`, `reordenarConOtraListaSeRechaza`, `quitarImagenBorraElArchivo`, `quitarUnaImagenAjenaNoBorraNada`; `ProductosIntegracionTest.imagenesSeSubenReordenanSirvenYQuitan`, `reordenarConOtraListaSeRechaza`; front: «hace principal otra foto», «quita una foto después de confirmar» |
| RF-9 | `ProductoServiceTest.desactivarYActivar`; `ProductosIntegracionTest.desactivarYActivarYFiltrarPorEstado`; front: «desactiva después de confirmar…», «activa sin preguntar», «con el filtro de estado vuelve a pedir la lista» |
| RF-10 | `ProductoRepositoryTest.buscarCombinaCategoriaEstadoYTextoEnNombreCodigoYMarca`; `ProductosIntegracionTest.filtraPorCategoriaYTextoYPagina`; front: «muestra los productos de la página», «lee los filtros de la URL», «filtra por categoría y por estado…», «busca después de una pausa…», «pasa de página» |
| RF-11 | `ProductoServiceTest.categoriaInexistenteAlCrear`, `productoInexistente`; `ProductosIntegracionTest.categoriaOProductoInexistentesDevuelven404`; front: «explica un rechazo del backend» |
| RF-12 (HU-06 RF-10) | `CategoriaServiceTest.categoriaConProductosNoSeBorra`; `ProductosIntegracionTest.categoriaConProductosNoSeBorraYMuestraCuantosTiene`; front `categorias.page.spec.ts`: «una categoría con productos ofrece ocultarla…», «si el backend responde que tiene productos…», «una categoría oculta con productos solo explica…» |
| RF-13 (conteo de HU-06 RF-12) | `ProductoRepositoryTest.contarPorCategoriaCuentaActivosEInactivos`; `ProductosIntegracionTest.categoriaConProductosNoSeBorraYMuestraCuantosTiene` |

### Suites

- Backend `./mvnw test`: **332 pruebas**, 0 fallas (antes 298).
- Frontend `npx ng test --watch=false`: **453 pruebas** (antes 403); `npx ng build` sin avisos.

### Revisiones

- **`/security-review`:** sin vulnerabilidades. Formato por contenido, nombres
  generados y validados al leer y borrar, solo se quitan o sirven imágenes que
  son del producto, `nosniff` por defecto, búsqueda con parámetros y comodines
  escapados, tamaño de página acotado, precio validado igual que la columna.
  Se sumó la prueba de que un cliente recibe 403 al subir una foto y al
  cambiar el estado. Aceptado: las fotos guardan sus metadatos (EXIF) tal
  cual, y la foto de un producto inactivo se puede pedir conociendo su nombre
  (UUID); el catálogo (HU-09/HU-10) decide qué muestra.
- **`/code-review`:** 3 hallazgos, corregidos: después de guardar, un par vacío
  de la ficha quedaba en pantalla como «cambio sin guardar» (el formulario
  ahora toma lo guardado); con el filtro «Activos» el producto desactivado
  quedaba en la lista (se vuelve a pedir); «0.125» se explicaba como
  separador de miles. Sin problemas de bloqueos, transacciones ni H2/MySQL.
- **`web-design-guidelines`:** corregidos el foco perdido al quitar o reordenar
  fotos (va al título «Fotos») y al quitar la última fila de la ficha, la
  tarjeta del celular que separaba nombre y marca o precio y unidad, «Subir
  foto» sin aspecto deshabilitado mientras sube, y medidas sueltas en vez de
  `--app-area-tocable`. Descartado: llevar los estilos de `select`/`textarea` a
  `_formulario.scss` (mejora de orden, sin efecto visible).

### Prueba manual

10/10/2026, Fabio, con Docker: pasaron todos los escenarios (alta con ficha y
código, precios inválidos, ficha repetida o incompleta, cambio de categoría
sin cambiar el código, fotos —subir, principal, quinta, PDF rechazado,
quitar—, desactivar y activar con y sin filtro, búsqueda por categoría y
texto, categoría con productos que ofrece ocultarse, aviso de cambios sin
guardar, cliente sin acceso) en la computadora y con vista de celular.
Verificación cruzada de David pendiente para el PR. *27/10/2026:* no se hizo; David se retiró del proyecto el 21/10/2026.

### Distinto de lo planeado

- `ProductoService` devuelve DTOs y no entidades: con `open-in-view` apagado,
  la categoría y las colecciones perezosas se leen dentro de la transacción.
  La lista trae la categoría con `@EntityGraph` y las imágenes en lotes.
- La limpieza de archivos al terminar la transacción pasó a
  `ArchivosAlTerminar`, compartida con `CategoriaService`; `ImagenGuardada`
  quedó como clase propia.
- Crear y editar bloquean la categoría, para que no se borre mientras se le
  asigna un producto.
- Quitar una imagen que no es del producto no es un error: no cambia nada.
- Se agregó `ActivoRequest` (`{ activo }`) y `CaracteristicaResponse`.
- `/productos/nuevo` y `/productos/:id` son una sola ruta: al crear, la misma
  pantalla pasa a editar y conserva lo escrito.
- Las pruebas del frontend se escribieron junto con el código, sin verlas
  fallar antes.
