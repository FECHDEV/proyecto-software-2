# HU-22 — Fotos achicadas

Historia: `docs/historias/HU-22.md` (RF-1 a RF-12). Decisión:
`decisiones.md` → «Fotos achicadas». Issue #77.

## Tareas

1. **Dependencias** (`backend/pom.xml`, **se pide permiso antes**):
   `com.twelvemonkeys.imageio:imageio-jpeg` y `imageio-webp` **3.15.3** (la
   última en Maven Central el 28/10/2026). Java puro; se registran solas en
   `ImageIO` (SPI). `imageio-jpeg` trae `imageio-metadata`, que lee el EXIF.

2. **`Achicador`** (`service/Achicador.java`), sin estado:
   `Versiones achicar(byte[] contenido)`, con
   `record Versiones(byte[] de400, byte[] de1200)` y
   `static final int[] ANCHOS = {400, 1200}`.
   - Primero lee **solo las medidas** del encabezado (`ImageReader.getWidth`
     y `getHeight`, sin decodificar). Más de 8000 px de lado o 40 MP →
     `ImagenConDemasiadosPixelesException` (400,
     `IMAGEN_CON_DEMASIADOS_PIXELES`). Sin lector o un error al leer →
     `ImagenInvalidaException` (RF-5, RF-6).
   - Decodifica con `ImageReadParam.setSourceSubsampling` para quedar cerca
     de 2 × 1200 px de ancho. Una foto de 40 MP no ocupa 160 MB en memoria.
   - Orientación EXIF (solo JPEG): la lee con `JPEGSegmentUtil` y
     `TIFFReader` de TwelveMonkeys, y gira o espeja antes de achicar (RF-3).
   - Pinta sobre fondo blanco (`TYPE_INT_RGB`), así lo transparente queda
     blanco (RF-4).
   - Achica a la mitad en pasos, con interpolación bilineal, hasta el ancho
     final: queda nítido sin el costo de `SCALE_AREA_AVERAGING`. **Nunca
     agranda** (RF-2).
   - Escribe JPEG con calidad 0,82 y sin metadatos.
   - Pruebas (`AchicadorTest`, con imágenes generadas en la prueba y una WebP
     chica en `src/test/resources/fotos/`):
     - `achicaA400Y1200ConLaMismaProporcion` (RF-1);
     - `unaFotoAngostaNoSeAgranda` (RF-2);
     - `respetaLaOrientacionExif` (RF-3; JPEG con orientación 6, 300 × 200 →
       versión de 200 × 300);
     - `loTransparenteQuedaBlanco` (RF-4);
     - `rechazaMasDe8000PxSinDecodificar` (RF-5; PNG con solo el encabezado
       IHDR de 20 000 × 20 000);
     - `rechazaMasDe40Megapixeles` (RF-5; 7000 × 6000 declarado);
     - `rechazaUnJpegCortado` (RF-6);
     - `leeWebP` (RF-1, decisión);
     - `unaWebPAnimadaUsaElPrimerCuadro`, si se consigue una chica de
       prueba; si no, queda como caso límite manual.

3. **Guardar, servir y borrar las versiones.**
   - `AlmacenImagenes`:
     - el nombre generado acepta además `<uuid>-400.jpg` y `<uuid>-1200.jpg`;
     - `guardarVersion(String original, int ancho, byte[] jpeg)` escribe a un
       temporal y lo mueve (`ATOMIC_MOVE`): nunca se sirve una versión a
       medias;
     - `leerVersion(String original, int ancho)`;
     - `tieneVersiones(String original)`;
     - `borrarConVersiones(String original)`.
   - `ProductoService.agregarImagen`:
     - achica **antes** de bloquear la fila: el trabajo de CPU queda fuera
       del bloqueo, y una foto rechazada no bloquea nada;
     - después guarda el original y las dos versiones;
     - si la transacción se revierte, se borran los tres (`ArchivosAlTerminar`
       acepta varios nombres).
   - `quitarImagen`: `borrarConVersiones` al confirmar (RF-9).
   - `imagen(idProducto, imagen, Integer ancho)`: con ancho, la versión, o el
     original si todavía no está (RF-7, RF-8).
   - `ProductosController`:
     - `GET /api/productos/{id}/imagenes/{imagen}?ancho=400|1200`, con la
       caché de un año;
     - otro `ancho` (`abc`, `-1`, `999`) → 400 con `errores[{campo:"ancho"}]`
       (RF-8).
   - Pruebas (`ProductosIntegracionTest`):
     - `subirUnaFotoGuardaSusVersiones` (RF-1, RF-7: el JPEG servido mide 400
       de ancho);
     - `unAnchoInvalidoResponde400` (RF-8);
     - `sinLaVersionSirveElOriginal` (RF-8; se borra la versión a mano);
     - `quitarUnaFotoBorraSusVersiones` (RF-9);
     - `unaFotoEnormeSeRechazaSinGuardarNada` (RF-5);
     - `unaFotoDañadaSeRechaza` (RF-6);
     - `elAnchoDeOtroProductoDa404` (caso límite).

4. **Las fotos ya subidas** (`service/VersionesFaltantes.java`):
   - es un `ApplicationRunner` que lanza un hilo aparte y vuelve enseguida
     (RF-10);
   - recorre los productos; por cada foto sin versiones (`tieneVersiones`),
     la lee, la achica y guarda las versiones;
   - después **vuelve a leer el producto**: si la foto ya no es suya
     (se quitó mientras tanto), borra las versiones recién guardadas. Así no
     quedan huérfanas, sin bloquear la fila durante el achique;
   - una foto que no se puede achicar se anota con `log.warn` y se sigue con
     la siguiente;
   - no corre con el perfil `test`: las pruebas lo llaman directo.
   - Pruebas (`VersionesFaltantesIntegracionTest`):
     - `generaLasQueFaltan`;
     - `noRegeneraLasQueYaEstan` (la fecha del archivo no cambia);
     - `unaFotoDañadaSeSalteaYSigue`;
     - `unaFotoQuitadaDuranteElAchiqueNoDejaHuerfanas` (con un `Achicador`
       que avisa en el medio y la quita).
   - `CatalogoDemoIntegracionTest.lasFotosDeLaDemoTienenVersiones` (RF-12;
     pasa por `agregarImagen`).

5. **Frontend:**
   - `core/modelos/catalogo.ts`:
     - `urlDeImagen(idProducto, imagen, ancho?: 400 | 1200)` agrega
       `?ancho=`;
     - `srcsetDeImagen(idProducto, imagen)` devuelve
       `"…?ancho=400 400w, …?ancho=1200 1200w"`.
   - **Tarjeta** (`DatosDeTarjeta` suma `srcset`): `src` de 400, `srcset` y
     `sizes` según la grilla (dos columnas en el celular, unos 260 px en
     escritorio). Así en pantallas de alta densidad baja la de 1200.
   - **Galería** (`shared/galeria`): `fotos` pasa a
     `FotoDeGaleria { src, srcset, original, miniatura }`.
     - La grande va con `srcset` y `sizes` («100vw» en el celular, «60vw» en
       escritorio).
     - Las miniaturas, con la de 400.
     - La pantalla completa, con el original (RF-11).
   - **Carrito, lista de productos del panel, fotos en la edición del
     producto:** la de 400. **Póster del visor 3D:** la de 1200.
   - `mensajes-error.ts`: `IMAGEN_CON_DEMASIADOS_PIXELES` → «La foto es
     demasiado grande: hasta 8000 px de lado y 40 megapíxeles.»
   - Pruebas:
     - `catalogo.spec.ts`: `urlDeImagenConAncho` y `srcsetConLasDosVersiones`;
     - `tarjeta-producto.spec.ts`: `laTarjetaPideLa400ConSrcset`;
     - `galeria.spec.ts`: `laGrandeUsaSrcset`,
       `lasMiniaturasUsanLa400`, `pantallaCompletaUsaElOriginal`;
     - `carrito.page.spec.ts`, `productos.page.spec.ts` y
       `producto.page.spec.ts`: la de 400;
     - `mensajes-error.spec.ts`: el mensaje nuevo.

6. **Docs:** `decisiones.md` → «Imágenes subidas» (las versiones, la
   dirección con `ancho`); el «Resultado» acá.

## Decisiones

- **Achicar en la subida y no al pedirla:** cada foto se achica una vez, y
  servir es leer un archivo.
- **`?ancho=` y no otra ruta:** el nombre de la foto sigue siendo uno solo;
  `ancho` solo elige entre lo que ya existe y no genera nada a pedido.
- **Un hilo aparte al arrancar, sin bloquear la fila:** se vuelve a leer el
  producto al final, para no frenar al Administrador mientras se achican
  decenas de fotos.
- **Calidad 0,82 y achique en pasos bilineales:** a 400 px no se nota
  diferencia con 0,9 y pesa un 30 % menos.
