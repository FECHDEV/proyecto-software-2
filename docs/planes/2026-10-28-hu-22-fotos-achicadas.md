# HU-22 — Fotos achicadas

Historia: `docs/historias/HU-22.md` (RF-1 a RF-12). Decisión:
`decisiones.md` → «Fotos achicadas». Issue #77. Corregido el 28/10/2026 tras
`/sdd:analizar`: el original servido en lugar de una versión va sin caché,
la demostración valida antes de cargar, código de lo delicado y más pruebas.

## Tareas

1. **Dependencias** (`backend/pom.xml`, aprobadas por Fabio, ya en la rama):
   `com.twelvemonkeys.imageio:imageio-jpeg` y `imageio-webp` **3.15.3**. Java
   puro; se registran solas en `ImageIO` (SPI). `imageio-jpeg` trae
   `imageio-metadata`, que lee el EXIF.

2. **`Achicador`** (`service/Achicador.java`, componente sin estado):
   `Versiones achicar(byte[] contenido)`, con
   `record Versiones(byte[] de400, byte[] de1200)`, `ANCHOS = {400, 1200}`,
   `MAXIMO_LADO = 8000` y `MAXIMO_PIXELES = 40_000_000`.
   `ImagenConDemasiadosPixelesException` (400,
   `IMAGEN_CON_DEMASIADOS_PIXELES`, «La foto admite hasta 8000 px de lado y
   40 megapíxeles.»).

   **Delicado (seguridad: una foto de 1 MB puede declarar 20 000 × 20 000 px,
   que abierta ocupa 1,6 GB):** las medidas salen del encabezado, antes de
   decodificar, y la foto se abre ya reducida.

   ```java
   public Versiones achicar(byte[] contenido) {
   	try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
   		Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
   		if (!lectores.hasNext()) {
   			throw new ImagenInvalidaException();
   		}
   		ImageReader lector = lectores.next();
   		try {
   			lector.setInput(entrada, true, true);
   			int ancho = lector.getWidth(0);
   			int alto = lector.getHeight(0);
   			// Antes de decodificar: lo que pesa en memoria son los píxeles, no el archivo
   			if (ancho > MAXIMO_LADO || alto > MAXIMO_LADO || (long) ancho * alto > MAXIMO_PIXELES) {
   				throw new ImagenConDemasiadosPixelesException();
   			}
   			ImageReadParam parametros = lector.getDefaultReadParam();
   			// Se lee ya reducida, a no menos del doble de la versión más grande: menos memoria y el
   			// achique en pasos conserva la nitidez
   			int salto = Math.max(1, ancho / (2 * ANCHOS[1]));
   			parametros.setSourceSubsampling(salto, salto, 0, 0);
   			BufferedImage leida = lector.read(0, parametros);
   			BufferedImage derecha = enderezar(leida, orientacion(contenido));
   			return new Versiones(jpeg(reducir(derecha, ANCHOS[0])), jpeg(reducir(derecha, ANCHOS[1])));
   		} finally {
   			lector.dispose();
   		}
   	} catch (ErrorNegocioException rechazo) {
   		throw rechazo;
   	} catch (IOException | RuntimeException danada) {
   		// Empieza como JPG, PNG o WebP pero no se puede abrir: el navegador tampoco la mostraría
   		throw new ImagenInvalidaException();
   	}
   }
   ```

   - `orientacion`: el valor 1 a 8 del EXIF, solo en JPEG (`JPEGSegmentUtil`
     y `TIFFReader`); sin EXIF, 1. `enderezar` gira o espeja (RF-3).
   - `reducir`: pinta sobre `TYPE_INT_RGB` con fondo blanco (RF-4) y achica
     a la mitad en pasos bilineales hasta el ancho final; **nunca agranda**
     (RF-2).
   - `jpeg`: calidad 0,82, sin metadatos.
   - Pruebas (`AchicadorTest`; las imágenes se generan en la prueba, y en
     `src/test/resources/fotos/` van una WebP, una WebP animada y un JPEG CMYK
     chicos):
     - `achicaA400Y1200ConLaMismaProporcion` (RF-1);
     - `unaFotoAngostaNoSeAgranda` (RF-2);
     - `respetaLaOrientacionExif` (RF-3; JPEG con orientación 6, 300 × 200 →
       200 × 300);
     - `loTransparenteQuedaBlanco` (RF-4);
     - `rechazaMasDe8000PxSinDecodificar` (RF-5; un PNG de solo el
       encabezado IHDR de 20 000 × 20 000, de menos de 100 bytes);
     - `rechazaMasDe40Megapixeles` (RF-5; 7000 × 6000 declarado);
     - `rechazaUnJpegCortado` (RF-6);
     - `leeWebP` (RF-1);
     - `unaWebPAnimadaUsaElPrimerCuadro` (caso límite);
     - `leeUnJpegCmyk` (caso límite: el resultado no sale con los colores
       invertidos).

3. **Guardar, servir y borrar las versiones.**
   - `AlmacenImagenes`:
     - el nombre generado acepta además `<uuid>-400.jpg` y `<uuid>-1200.jpg`;
     - `String guardarVersion(String original, int ancho, byte[] jpeg)`:
       escribe a un temporal en la misma carpeta y lo mueve con
       `ATOMIC_MOVE`, así nunca se sirve una versión a medias;
     - `Optional<byte[]> leerVersion(String original, int ancho)`;
     - `boolean tieneVersiones(String original)`: las dos;
     - `void borrarVersiones(String original)`.
   - **Delicado (transacción):** `ArchivosAlTerminar.borrar` pasa a recibir
     listas, `borrar(almacen, List<String> siSeConfirma, List<String>
     siSeRevierte)`. La lista de lo que se revierte se registra **antes** de
     escribir, y se llena a medida que se guarda: si falla la segunda
     versión, se borran el original y la primera. Los usos actuales pasan a
     `List.of(…)`.

     ```java
     @Transactional
     public ProductoResponse agregarImagen(Long idProducto, byte[] contenido) {
     	if (contenido.length > FormatoImagen.MAXIMO) {
     		throw new ImagenDemasiadoGrandeException();
     	}
     	FormatoImagen formato = FormatoImagen.detectar(contenido).orElseThrow(ImagenInvalidaException::new);
     	// Antes de bloquear: el trabajo de CPU no frena a nadie, y una foto rechazada no bloquea nada
     	Achicador.Versiones versiones = achicador.achicar(contenido);
     	Producto producto = bloquear(idProducto);
     	if (producto.getImagenes().size() >= MAXIMO_IMAGENES) {
     		throw new ProductoLlenoDeImagenesException();
     	}
     	List<String> guardados = new ArrayList<>();
     	ArchivosAlTerminar.borrar(almacen, List.of(), guardados);
     	String nueva = almacen.guardar(contenido, formato);
     	guardados.add(nueva);
     	guardados.add(almacen.guardarVersion(nueva, 400, versiones.de400()));
     	guardados.add(almacen.guardarVersion(nueva, 1200, versiones.de1200()));
     	producto.agregarImagen(nueva);
     	return ProductoResponse.from(producto);
     }
     ```

   - `quitarImagen`: al confirmar, borra el original y sus versiones (RF-9).
   - `imagen(idProducto, imagen, Integer ancho)` devuelve
     `ImagenGuardada(contenido, formato, nombre, boolean achicada)`. Con un
     ancho, la versión (`achicada = true`) o, si todavía no está, el original
     (`achicada = false`).
   - `ProductosController`, `GET /api/productos/{id}/imagenes/{imagen}?ancho=`:
     - **un año de caché solo si se sirve lo pedido**: el original pedido sin
       ancho, o la versión. El original servido en lugar de una versión va
       con `no-cache` (RF-7, RF-8);
     - otro ancho (`abc`, `-1`, `999`) → 400 `DATOS_INVALIDOS` con
       `errores[{campo:"ancho"}]` (RF-8).
   - Pruebas (`ProductosIntegracionTest`):
     - `subirUnaFotoGuardaSusVersiones` (RF-1, RF-7): el JPEG servido mide 400
       de ancho y lleva un año de caché;
     - `unAnchoInvalidoResponde400` (RF-8);
     - `sinLaVersionSirveElOriginalSinCache` (RF-8; la versión se borra a
       mano);
     - `quitarUnaFotoBorraSusVersiones` (RF-9);
     - `unaFotoEnormeSeRechazaSinGuardarNada` (RF-5: la carpeta queda igual);
     - `unaFotoDañadaSeRechaza` (RF-6);
     - `elAnchoDeOtroProductoDa404` (caso límite).
   - `ArchivosAlTerminarTest.siFallaLaSegundaVersionSeBorraLoGuardado` (con
     un almacén que falla en la segunda escritura).

4. **Las fotos ya subidas y la demostración.**
   - `VersionesFaltantes` (`service/VersionesFaltantes.java`):
     - es un `ApplicationRunner` que arranca un hilo aparte (`Thread.ofPlatform().name("versiones-faltantes")`)
       y vuelve enseguida (RF-10);
     - con `@Profile("!test")`: las pruebas llaman a `completar()` directo.

     **Delicado (concurrencia con «quitar foto», sin bloquear la fila):**

     ```java
     void completar() {
     	for (Long idProducto : productoRepository.idsConImagenes()) {
     		for (String foto : fotosDe(idProducto)) {
     			if (almacen.tieneVersiones(foto)) {
     				continue;
     			}
     			try {
     				Optional<byte[]> original = almacen.leer(foto);
     				if (original.isEmpty()) {
     					continue;
     				}
     				Achicador.Versiones versiones = achicador.achicar(original.get());
     				almacen.guardarVersion(foto, 400, versiones.de400());
     				almacen.guardarVersion(foto, 1200, versiones.de1200());
     				// Si se quitó mientras se achicaba, quitarImagen ya borró lo que había y estas quedarían
     				// huérfanas. Si se quita después de esta lectura, quitarImagen las borra al confirmar
     				if (!fotosDe(idProducto).contains(foto)) {
     					almacen.borrarVersiones(foto);
     				}
     			} catch (ErrorNegocioException | UncheckedIOException error) {
     				log.warn("La foto {} del producto {} queda sin versiones: {}", foto, idProducto,
     						error.getMessage());
     			}
     		}
     	}
     }
     ```

     `fotosDe` lee las fotos del producto en su propia transacción de
     lectura; `idsConImagenes` es una consulta nueva en `ProductoRepository`.
   - **Catálogo de demostración:** `CatalogoDemo.imagenes` suma
     `achicador.achicar(contenido)` a la validación. Si la foto es enorme o
     está dañada, se agrega a los errores y la carga no empieza (RF-12). Al
     cargar, pasa por `agregarImagen`.
   - Pruebas:
     - `VersionesFaltantesIntegracionTest`:
       - `generaLasQueFaltan` (RF-10);
       - `noRegeneraLasQueYaEstan` (RF-10: la fecha del archivo no cambia);
       - `unaFotoDañadaOEnormeSeSalteaYSigue` (RF-10: un archivo de cada una,
         y la tercera foto igual queda con versiones);
       - `unaFotoQuitadaDuranteElAchiqueNoDejaHuerfanas` (con un
         `Achicador` que la quita en el medio);
       - `elArranqueNoEsperaAlAchique` (RF-10: con un `Achicador` que espera
         un `CountDownLatch`, `run()` vuelve antes de que se libere).
     - `CatalogoDemoIntegracionTest`:
       - `lasFotosDeLaDemoTienenVersiones` (RF-12);
       - `unaFotoDeLaDemoEnormeSeInformaAntesDeCargar` (RF-12: no queda
         ningún producto).

5. **Frontend:**
   - `core/modelos/catalogo.ts`:
     - `urlDeImagen(idProducto, imagen, ancho?: 400 | 1200)` agrega
       `?ancho=`;
     - `srcsetDeImagen(idProducto, imagen)` devuelve
       `"…?ancho=400 400w, …?ancho=1200 1200w"`.
   - **Tarjeta** (`DatosDeTarjeta` suma `srcset`): `src` de 400, más `srcset`
     y `sizes` según la grilla (la mitad de la pantalla en el celular, unos
     260 px en escritorio). En pantallas de alta densidad baja la de 1200.
   - **Galería** (`shared/galeria`): `fotos` pasa a
     `FotoDeGaleria { src, srcset, original, miniatura }`, armado en el
     detalle (`features/`), así `shared/` no conoce la API.
     - La grande, con `srcset` y `sizes` («100vw» en el celular, «60vw» en
       escritorio).
     - Las miniaturas, con la de 400.
     - La pantalla completa, con el original (RF-11).
   - **Carrito, lista de productos del panel y fotos en la edición del
     producto:** la de 400. **Póster del visor 3D:** la de 1200 (RF-11).
   - `mensajes-error.ts`: `IMAGEN_CON_DEMASIADOS_PIXELES` → «La foto es
     demasiado grande: hasta 8000 px de lado y 40 megapíxeles.»
   - Pruebas:
     - `catalogo.spec.ts`: `urlDeImagenConAncho`, `srcsetConLasDosVersiones`;
     - `tarjeta-producto.spec.ts`: `laTarjetaPideLa400ConSrcset`;
     - `galeria.spec.ts`: `laGrandeUsaSrcset`, `lasMiniaturasUsanLa400`,
       `pantallaCompletaUsaElOriginal`;
     - `detalle.page.spec.ts`: `elPosterDelVisorEsLa1200`;
     - `carrito.page.spec.ts`, `productos.page.spec.ts` y
       `producto.page.spec.ts`: la de 400;
     - `mensajes-error.spec.ts`: el mensaje nuevo.

6. **Docs:** `decisiones.md` → «Imágenes subidas» (las versiones, `ancho` y la
   caché) y «Catálogo de demostración» (la validación nueva); el «Resultado»
   acá.

## Decisiones

- **Achicar en la subida y no al pedirla:** cada foto se achica una vez, y
  servir es leer un archivo.
- **`?ancho=` y no otra ruta:** el nombre de la foto sigue siendo uno solo, y
  `ancho` solo elige entre lo que ya existe; no genera nada a pedido.
- **El original servido en lugar de una versión va sin caché:** con un año,
  el navegador lo seguiría usando bajo la dirección de la versión aunque
  después la versión exista.
- **Un hilo aparte al arrancar, sin bloquear la fila:** se vuelve a leer el
  producto al final, para no frenar al Administrador mientras se achican
  decenas de fotos.
- **`CatalogoDemo` y `VersionesFaltantes` arrancan a la vez sin pisarse:** la
  demostración guarda las versiones dentro de `agregarImagen`, antes de
  confirmar, y `VersionesFaltantes` solo genera las que faltan.
- **Calidad 0,82 y achique en pasos bilineales:** a 400 px no se nota
  diferencia con 0,9 y pesa un 30 % menos.

## Revisiones (al cerrar)

Las de `docs/flujo-sdd.md` → «Revisión antes del PR». A esta historia le
tocan:
- el **subagente de interfaz**, porque cambian las fotos en pantalla;
- **`/code-review`**;
- **`/security-review`**, porque procesa archivos subidos: datos de afuera
  que se decodifican.
