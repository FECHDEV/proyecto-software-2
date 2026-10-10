# Plan — HU-06 Categorías

Historia: `docs/historias/HU-06.md` · Issue #9 · Rama `feature/hu-06-categorias`

Historia nueva: entidad, API pública y de gestión, y la pantalla del
Administrador. El catálogo que muestra las categorías es de HU-09; RF-10 y las
partes de RF-8 y RF-12 que dependen de los productos se prueban en HU-07.

## Tarea 1 — Entidad y repositorio · backend

**Archivos:** `entity/Categoria.java`, `repository/CategoriaRepository.java`.

- `Categoria`: `idCategoria` (`Long`, autoincremental), `nombre` (60),
  `nombreClave` (60, **único**: el nombre recortado y en minúsculas, para el
  duplicado sin importar mayúsculas), `descripcion` (300, nula), `imagen` (el
  nombre del archivo que genera `AlmacenImagenes`, nulo), `visible`
  (`boolean`). Fábrica `Categoria.crear(nombre, descripcion)` y métodos
  `editar`, `ocultar`, `mostrar`, `cambiarImagen`.
- `CategoriaRepository`: `findAllByOrderByNombreClave()`,
  `findByVisibleTrueOrderByNombreClave()`, `existsByNombreClave(..)`,
  `esNombreDuplicado(DataIntegrityViolationException)` (como en
  `UsuarioRepository`).

**Pruebas** (`CategoriaRepositoryTest`, con `flush`/`clear`):
- `guardaYLeeTodasLasColumnas` — HU-06 RF-2
- `nombreRepetidoSinImportarMayusculasSeReconoceComoDuplicado` — HU-06 RF-4
- `listaTodasEnOrdenAlfabetico` y `soloVisiblesEnOrdenAlfabetico` — HU-06 RF-11, RF-12

## Tarea 2 — Servicio de gestión · backend

**Archivos:** `service/CategoriaService.java`, `dto/CategoriaRequest.java`,
`dto/CategoriaResponse.java`, `dto/CategoriaGestionResponse.java`,
`exception/CategoriaExistenteException.java` (409 `CATEGORIA_EXISTENTE`),
`exception/CategoriaNoEncontradaException.java` (404 `CATEGORIA_NO_ENCONTRADA`),
`exception/ImagenInvalidaException.java` si no existe ya un error para eso.

- `CategoriaRequest(nombre, descripcion)`: `@NotBlank`, `@Size(max=60)`,
  `@Size(max=300)`; recorta en el constructor compacto.
- `crear`, `editar(id, …)`: duplicado por `existsByNombreClave` (excluyendo la
  propia al editar) y por la restricción única (`saveAndFlush` +
  `esNombreDuplicado`), como en el registro.
- `ocultar(id)`, `mostrar(id)`, `borrar(id)` (borra la fila y después el
  archivo).
- `cambiarImagen(id, bytes)`: detecta el formato por el contenido
  (`FormatoImagen.detectar`), guarda el archivo nuevo, actualiza la fila y
  **después del commit** borra el anterior; si la transacción falla, borra el
  nuevo. Código completo, porque toca archivos y transacción:

```java
@Transactional
public Categoria cambiarImagen(Long id, byte[] contenido) {
	FormatoImagen formato = FormatoImagen.detectar(contenido).orElseThrow(ImagenInvalidaException::new);
	Categoria categoria = buscar(id);
	String anterior = categoria.getImagen();
	String nueva = almacen.guardar(contenido, formato);
	TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
		@Override
		public void afterCompletion(int estado) {
			almacen.borrar(estado == STATUS_COMMITTED ? anterior : nueva); // borrar(null) no hace nada
		}
	});
	categoria.cambiarImagen(nueva);
	return categoria;
}
```

**Pruebas** (`CategoriaServiceTest`):
- `creaVisibleConNombreYDescripcionRecortados` — HU-06 RF-2, RF-3
- `nombreRepetidoSeRechazaSinImportarMayusculas` — HU-06 RF-4
- `dosCreacionesSimultaneasTerminanComoCategoriaExistente` — HU-06 RF-4
- `editarAlMismoNombreConOtrasMayusculasSePermite` — HU-06 RF-5
- `ocultarYMostrar` — HU-06 RF-8
- `borrarQuitaLaFilaYLaImagen` — HU-06 RF-9
- `imagenValidaReemplazaLaAnteriorDespuesDelCommit` — HU-06 RF-6
- `imagenQueNoEsJpgPngNiWebpSeRechazaSinTocarLaCategoria` — HU-06 RF-7
- `categoriaInexistente` — HU-06 RF-13

## Tarea 3 — API y seguridad · backend

**Archivos:** `controller/CategoriasController.java`,
`controller/GestionCategoriasController.java`, `config/SecurityConfig.java`.

| Método | Ruta | Quién | Qué |
|---|---|---|---|
| GET | `/api/categorias` | público | visibles, alfabético (RF-11) |
| GET | `/api/categorias/{id}/imagen` | público | la imagen, con su tipo de contenido |
| GET | `/api/gestion/categorias` | Administrador | todas, con `visible` y `productos` (0 hasta HU-07) (RF-12) |
| POST | `/api/gestion/categorias` | Administrador | crear (201) |
| PUT | `/api/gestion/categorias/{id}` | Administrador | editar |
| PUT | `/api/gestion/categorias/{id}/visible` | Administrador | `{ "visible": true/false }` |
| PUT | `/api/gestion/categorias/{id}/imagen` | Administrador | multipart `imagen` |
| DELETE | `/api/gestion/categorias/{id}/imagen` | Administrador | quitar la imagen |
| DELETE | `/api/gestion/categorias/{id}` | Administrador | borrar (204) |

`SecurityConfig`: `GET /api/categorias/**` público y `/api/gestion/**` con rol
ADMINISTRADOR.

**Pruebas:**
- `CategoriasControllerTest` (MockMvcTester): públicas sin sesión, gestión 401
  sin sesión y 403 a un Empleado — HU-06 RF-1, RF-11
- `CategoriasIntegracionTest`: crear, duplicado, editar, ocultar, borrar,
  subir y servir una imagen real, imagen basura, id basura → 400/404 — HU-06
  RF-1 a RF-9, RF-11 a RF-13

## Tarea 4 — Pantalla «Categorías» · frontend

**Archivos:** `core/modelos/categoria.ts`, `core/api/categorias.service.ts`,
`features/categorias/categorias.page.{ts,html,scss}`, `app.routes.ts`,
`app.component.ts` (módulo «Categorías» solo para el Administrador).

- Lectura con `httpResource`; escrituras con `HttpClient`.
- Una lista (nombre, imagen o ícono genérico de Lucide, estado, productos) y
  un formulario con Signal Forms para crear y editar (nombre, descripción).
  Por fila: editar, cambiar imagen (input de archivo), quitar imagen,
  ocultar/mostrar y borrar con el diálogo de confirmación existente.
- Mensajes nuevos en `mensajes-error.ts`: `CATEGORIA_EXISTENTE`,
  `CATEGORIA_NO_ENCONTRADA`, `IMAGEN_INVALIDA` (o el código que ya exista).

**Pruebas** (`categorias.service.spec.ts`, `categorias.page.spec.ts`):
- servicio: «pide las de gestión», «crea con los datos recortados», «sube la
  imagen como multipart» — HU-06 RF-2, RF-6, RF-12
- pantalla: «lista todas con su estado», «crea una categoría», «avisa un nombre
  repetido junto al campo», «frena un nombre vacío o de más de 60», «oculta y
  muestra», «borrar pide confirmación nombrándola», «sin imagen muestra el
  ícono genérico», «avisa una imagen inválida» — HU-06 RF-2 a RF-8, RF-12, RF-14
- `app.routes.spec.ts`: «la gestión de categorías es solo del administrador» —
  HU-06 RF-1

## Decisiones

- Dos controladores: la lectura pública (`/api/categorias`) separada de la
  gestión (`/api/gestion/categorias`), así la regla de seguridad es por
  prefijo.
- `nombreClave` único en la base para el duplicado sin importar mayúsculas, en
  vez de una consulta `lower()`: la restricción también cubre la carrera.
- El archivo viejo se borra después del commit y el nuevo si se revierte: un
  fallo no deja la categoría apuntando a un archivo borrado.

## Revisiones antes del PR

Las de `docs/flujo-sdd.md` → «Revisión antes del PR»: las tres (subida de
archivos, endpoint público nuevo y frontend).

## Resultado

*10/10/2026, `/sdd:cerrar HU-06`.*

### Cobertura RF → pruebas

| RF | Pruebas |
|---|---|
| RF-1 | `CategoriasIntegracionTest.sinSesionLaGestionDevuelve401YUnClienteRecibe403`; `app.routes.spec.ts` «la gestión de categorías es solo del administrador»; `app.component.spec.ts` (menú); `panel.page.spec.ts` (acceso) |
| RF-2 | `CategoriaRepositoryTest.guardaYLeeTodasLasColumnas`; `CategoriaServiceTest.creaVisibleConNombreYDescripcionRecortados`; `CategoriasIntegracionTest.creaListaParaGestion…`; front: «crea una categoría…», servicio «crea con los datos recortados…» |
| RF-3 | `CategoriasIntegracionTest.nombreRepetidoVacioOLargoSeRechaza`, `nombreDe60CaracteresQueCrecenEnMinusculasSeAcepta`; front: «frena un nombre vacío o de más de 60 caracteres» |
| RF-4 | `CategoriaRepositoryTest.nombreRepetido…`; `CategoriaServiceTest.nombreRepetidoSeRechaza…`, `dosCreacionesSimultaneas…`, `renombrarAlNombreDeOtraSeRechaza`; `CategoriasIntegracionTest.nombreQueSoloDifiereEnTildesSeRechaza`; front: «avisa un nombre repetido junto al campo», servicio «rechaza con CATEGORIA_EXISTENTE» |
| RF-5 | `CategoriaServiceTest.editarAlMismoNombreConOtrasMayusculasSePermite`; `CategoriasIntegracionTest.editaNombreYDescripcion`; front: «edita una categoría con el mismo formulario» |
| RF-6 | `CategoriasIntegracionTest.subeSirveYQuitaLaImagen`, `cambiarLaImagenBorraElArchivoAnterior`; front: «muestra la imagen o el ícono genérico», «sube la imagen elegida…», servicio «sube la imagen como multipart» |
| RF-7 | `CategoriaServiceTest.imagenQueNoEsJpgPngNiWebpSeRechaza…`; `CategoriasIntegracionTest.imagenQueNoEsJpgPngNiWebpSeRechaza`; front: «una subida rechazada no vuelve a pedir las imágenes» |
| RF-8 | `CategoriaServiceTest.ocultarYMostrar`; `CategoriasIntegracionTest.creaListaParaGestion…`; front: «oculta y muestra». **La parte de los productos se prueba en HU-07** |
| RF-9 | `CategoriaServiceTest.borrarQuitaLaFilaYLaImagen`, `borrarSinImagenNoTocaLosArchivos`; `CategoriasIntegracionTest.borraUnaCategoriaYDespuesNoLaEncuentra`; front: «borrar la categoría que se edita sale del modo edición» |
| RF-10 | **Se prueba en HU-07** (anotado en el issue #10) |
| RF-11 | `CategoriaRepositoryTest.soloVisiblesEnOrdenAlfabetico`; `CategoriasIntegracionTest.creaListaParaGestion…` |
| RF-12 | `CategoriaRepositoryTest.listaTodasEnOrdenAlfabetico`; front: «lista todas con su estado y sus productos». **El conteo real de productos, en HU-07** |
| RF-13 | `CategoriaServiceTest.categoriaInexistente`; `CategoriasIntegracionTest.borraUnaCategoriaYDespuesNoLaEncuentra`, `identificadorBasuraNoEsUnErrorDelServidor` |
| RF-14 | front: «borrar pide confirmación nombrándola», «cancelar el borrado no borra nada» |

### Suites

- Backend `./mvnw test`: **298 pruebas**, 0 fallas (antes 273).
- Frontend `npx ng test --watch=false`: **403 pruebas** (antes 380); `npx ng build` sin avisos.

### Revisiones

- **`/security-review`:** sin vulnerabilidades. El formato se reconoce por el
  contenido, 2 MB, nombre generado (UUID) y validado al leer, `nosniff` por
  defecto, `/api/gestion/**` solo para el Administrador. Aceptado: la imagen
  de una categoría oculta se puede pedir si se conoce su número (la pantalla
  de gestión la carga con `<img>`, que no lleva el token).
- **`/code-review`:** 9 hallazgos.
  - Corregidos: dos subidas a la vez podían dejar un archivo huérfano (bloqueo
    de la fila al cambiar o quitar la imagen y al borrar); un nombre de 60
    caracteres que crece al pasar a minúsculas daba 500 (`nombre_clave` de
    120); MySQL ignora tildes y H2 no (la clave también las quita: «Jardin» y
    «Jardín» son la misma, decisión actualizada); borrar la categoría en edición
    dejaba el formulario trabado; una subida rechazada volvía a pedir todas las
    imágenes; la detección del duplicado estaba copiada (ahora
    `ViolacionesDeIntegridad`, compartida con usuarios).
  - Descartados: la imagen pública de una categoría oculta (ver arriba); el
    recorte en tres capas (convención del proyecto; el DTO recorta antes de
    validar el largo); el borrado inmediato sin transacción (todos los que
    llaman son `@Transactional`).
- **`web-design-guidelines`:** el input de archivo ocultaba su foco; ahora el
  foco se ve en su etiqueta «Imagen». Estados con texto («Visible»/«Oculta») y
  borde punteado, no solo color.

### Prueba manual

10/10/2026, Fabio, con Docker: pasaron los escenarios (crear, nombre repetido
sin tildes, imagen válida y PDF rechazado, editar, ocultar y ver que no sale en
`/api/categorias`, borrar con confirmación, un cliente no ve la gestión).
Verificación cruzada de David pendiente para el PR.

### Distinto de lo planeado

- La subida de la imagen es `POST` (no `PUT`): `MockMvcTester` y el navegador
  manejan multipart con `POST`.
- El reemplazo de la imagen se prueba en la integración (con la transacción
  real), no con mocks; no hubo `CategoriasControllerTest` aparte, lo cubre la
  integración.
- Las pruebas del servicio del frontend se escribieron junto con el código, sin
  verlas fallar antes.
