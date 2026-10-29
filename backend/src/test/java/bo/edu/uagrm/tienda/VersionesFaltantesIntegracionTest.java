package bo.edu.uagrm.tienda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import bo.edu.uagrm.tienda.config.ImagenesProperties;
import bo.edu.uagrm.tienda.dto.ProductoRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.MovimientoStockRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.service.Achicador;
import bo.edu.uagrm.tienda.service.AlmacenImagenes;
import bo.edu.uagrm.tienda.service.FormatoImagen;
import bo.edu.uagrm.tienda.service.ProductoService;
import bo.edu.uagrm.tienda.service.VersionesAlArrancar;
import bo.edu.uagrm.tienda.service.VersionesFaltantes;

// HU-22 RF-10: las fotos subidas antes de la historia se achican solas al arrancar
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class VersionesFaltantesIntegracionTest {

	@Autowired
	private VersionesFaltantes versionesFaltantes;

	@Autowired
	private ProductoService productoService;

	@Autowired
	private ProductoRepository productoRepository;

	@Autowired
	private CategoriaRepository categoriaRepository;

	@Autowired
	private MovimientoStockRepository movimientoRepository;

	@Autowired
	private AlmacenImagenes almacen;

	@Autowired
	private Achicador achicador;

	@Autowired
	private ImagenesProperties imagenes;

	@Autowired
	private TransactionTemplate transaccion;

	private Long idProducto;

	@BeforeEach
	void crearProducto() {
		limpiar();
		Categoria categoria = categoriaRepository.save(Categoria.crear("Herramientas", null));
		idProducto = productoService.crear(new ProductoRequest("Martillo", null, null, categoria.getIdCategoria(),
				UnidadVenta.UNIDAD, new BigDecimal("50"), null, null, false)).idProducto();
	}

	@AfterEach
	void limpiar() {
		transaccion.execute(estado -> productoRepository.findAll().stream()
				.flatMap(producto -> producto.getImagenes().stream()).toList()).forEach(foto -> {
					almacen.borrar(foto);
					AlmacenImagenes.nombresDeVersiones(foto).forEach(almacen::borrar);
				});
		movimientoRepository.deleteAll();
		productoRepository.deleteAll();
		categoriaRepository.deleteAll();
	}

	// Como quedó una foto subida antes de HU-22: el original sin sus versiones
	private String fotoSinVersiones(byte[] contenido) {
		String foto = almacen.guardar(contenido, FormatoImagen.PNG);
		transaccion.executeWithoutResult(
				estado -> productoRepository.findById(idProducto).orElseThrow().agregarImagen(foto));
		return foto;
	}

	@Test
	void generaLasQueFaltan() {
		String foto = fotoSinVersiones(FotoDePrueba.png(1600, 1000));

		versionesFaltantes.completar();

		assertThat(almacen.tieneVersiones(foto)).isTrue();
	}

	@Test
	void noRegeneraLasQueYaEstan() throws IOException {
		String foto = fotoSinVersiones(FotoDePrueba.PNG);
		versionesFaltantes.completar();
		Path version = imagenes.directorio().resolve(AlmacenImagenes.nombreDeVersion(foto, 400));
		FileTime antes = FileTime.fromMillis(0);
		Files.setLastModifiedTime(version, antes);

		versionesFaltantes.completar();

		assertThat(Files.getLastModifiedTime(version)).isEqualTo(antes);
	}

	// Una dañada o enorme queda con el original, se anota, y la siguiente igual se achica
	@Test
	void unaFotoDanadaOEnormeSeSalteaYSigue(CapturedOutput salida) {
		String danada = fotoSinVersiones(FotoDePrueba.PNG_DANADO);
		String enorme = fotoSinVersiones(FotoDePrueba.soloEncabezadoPng(20_000, 20_000));
		String buena = fotoSinVersiones(FotoDePrueba.PNG);

		versionesFaltantes.completar();

		assertThat(almacen.tieneVersiones(danada)).isFalse();
		assertThat(almacen.tieneVersiones(enorme)).isFalse();
		assertThat(almacen.tieneVersiones(buena)).isTrue();
		assertThat(salida.getOut()).contains("La foto " + danada + " del producto " + idProducto + " queda sin versiones")
				.contains("La foto " + enorme + " del producto " + idProducto + " queda sin versiones");
	}

	// RF-10 (/code-review): cualquier error con una foto, no solo los previstos, no corta el recorrido
	@Test
	void unErrorInesperadoNoCortaElRecorrido() {
		String primera = fotoSinVersiones(FotoDePrueba.PNG);
		String segunda = fotoSinVersiones(FotoDePrueba.PNG);
		Achicador queFallaUnaVez = new Achicador() {
			private boolean fallo;

			@Override
			public Versiones achicar(byte[] contenido) {
				if (!fallo) {
					fallo = true;
					throw new IllegalStateException("inesperado");
				}
				return achicador.achicar(contenido);
			}
		};

		new VersionesFaltantes(productoRepository, almacen, queFallaUnaVez, transaccion).completar();

		assertThat(almacen.tieneVersiones(primera)).isFalse();
		assertThat(almacen.tieneVersiones(segunda)).isTrue();
	}

	// Caso límite: el Administrador la quita mientras se achica; sus versiones no quedan sueltas en la carpeta
	@Test
	void unaFotoQuitadaDuranteElAchiqueNoDejaHuerfanas() throws IOException {
		String foto = fotoSinVersiones(FotoDePrueba.PNG);
		Achicador queQuitaEnElMedio = new Achicador() {
			@Override
			public Versiones achicar(byte[] contenido) {
				productoService.quitarImagen(idProducto, foto);
				return achicador.achicar(contenido);
			}
		};

		new VersionesFaltantes(productoRepository, almacen, queQuitaEnElMedio, transaccion).completar();

		String uuid = foto.substring(0, foto.indexOf('.'));
		try (Stream<Path> archivos = Files.list(imagenes.directorio())) {
			assertThat(archivos.map(archivo -> archivo.getFileName().toString()).filter(n -> n.startsWith(uuid)))
					.isEmpty();
		}
	}

	// /code-review: quitada mientras se achica y con la segunda versión fallando, la primera no queda suelta
	@Test
	void siFallaLaSegundaVersionDeUnaQuitadaNoQuedaLaPrimera() throws IOException {
		String foto = fotoSinVersiones(FotoDePrueba.PNG);
		Achicador queQuitaEnElMedio = new Achicador() {
			@Override
			public Versiones achicar(byte[] contenido) {
				productoService.quitarImagen(idProducto, foto);
				return achicador.achicar(contenido);
			}
		};
		AlmacenImagenes queFallaEnLaSegunda = new AlmacenImagenes(imagenes) {
			@Override
			public String guardarVersion(String original, int ancho, byte[] jpeg) {
				if (ancho == 1200) {
					throw new UncheckedIOException(new IOException("disco lleno"));
				}
				return super.guardarVersion(original, ancho, jpeg);
			}
		};

		new VersionesFaltantes(productoRepository, queFallaEnLaSegunda, queQuitaEnElMedio, transaccion).completar();

		String uuid = foto.substring(0, foto.indexOf('.'));
		try (Stream<Path> archivos = Files.list(imagenes.directorio())) {
			assertThat(archivos.map(archivo -> archivo.getFileName().toString()).filter(n -> n.startsWith(uuid)))
					.isEmpty();
		}
	}

	// RF-10: el arranque no espera; el achique sigue en su hilo
	@Test
	void elArranqueNoEsperaAlAchique() throws InterruptedException {
		CountDownLatch liberar = new CountDownLatch(1);
		CountDownLatch termino = new CountDownLatch(1);
		VersionesFaltantes lenta = mock(VersionesFaltantes.class);
		willAnswer(invocacion -> {
			liberar.await(10, TimeUnit.SECONDS);
			termino.countDown();
			return null;
		}).given(lenta).completar();

		new VersionesAlArrancar(lenta).alArrancar();

		assertThat(termino.getCount()).isEqualTo(1);
		liberar.countDown();
		assertThat(termino.await(5, TimeUnit.SECONDS)).isTrue();
	}
}
