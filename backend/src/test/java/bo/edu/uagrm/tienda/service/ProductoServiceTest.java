package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.dto.CaracteristicaRequest;
import bo.edu.uagrm.tienda.dto.CaracteristicaResponse;
import bo.edu.uagrm.tienda.dto.ProductoRequest;
import bo.edu.uagrm.tienda.dto.ProductoResponse;
import bo.edu.uagrm.tienda.dto.ProductoResumenResponse;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.exception.CategoriaNoEncontradaException;
import bo.edu.uagrm.tienda.exception.FichaInvalidaException;
import bo.edu.uagrm.tienda.exception.ImagenDemasiadoGrandeException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;
import bo.edu.uagrm.tienda.exception.ModeloDemasiadoGrandeException;
import bo.edu.uagrm.tienda.exception.ModeloInvalidoException;
import bo.edu.uagrm.tienda.exception.OrdenDeImagenesInvalidoException;
import bo.edu.uagrm.tienda.exception.ProductoLlenoDeImagenesException;
import bo.edu.uagrm.tienda.exception.ProductoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.UnidadIncompatibleConStockException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;

// HU-07: productos
@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

	private static final ZoneId ZONA = ZoneId.of("America/La_Paz");
	private static final Instant AHORA = Instant.parse("2026-10-10T13:00:00Z");
	// Los primeros bytes de un PNG: alcanzan para que FormatoImagen lo reconozca
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0 };

	@Mock
	private ProductoRepository productoRepository;

	@Mock
	private CategoriaRepository categoriaRepository;

	@Mock
	private AlmacenImagenes almacen;

	private ProductoService servicio;

	@BeforeEach
	void crearServicio() {
		servicio = new ProductoService(productoRepository, categoriaRepository, almacen, Clock.fixed(AHORA, ZONA));
	}

	private Categoria categoria(long id, String nombre) {
		Categoria categoria = Categoria.crear(nombre, null);
		ReflectionTestUtils.setField(categoria, "idCategoria", id);
		given(categoriaRepository.findConBloqueoByIdCategoria(id)).willReturn(Optional.of(categoria));
		return categoria;
	}

	// El primer guardado le da el número al producto, como haría la base
	private void guardaConNumero(long idProducto) {
		given(productoRepository.saveAndFlush(any(Producto.class))).willAnswer(invocacion -> {
			Producto producto = invocacion.getArgument(0);
			ReflectionTestUtils.setField(producto, "idProducto", idProducto);
			return producto;
		});
	}

	private Producto existente(long id, Categoria categoria, String codigo) {
		Producto producto = sinBloqueo(id, categoria, codigo);
		given(productoRepository.findConBloqueoByIdProducto(id)).willReturn(Optional.of(producto));
		return producto;
	}

	private static Producto sinBloqueo(long id, Categoria categoria, String codigo) {
		Producto producto = Producto.crear("Regadera metálica", null, null, new BigDecimal("35.00"),
				UnidadVenta.UNIDAD, categoria, List.of(), LocalDateTime.ofInstant(AHORA, ZONA));
		ReflectionTestUtils.setField(producto, "idProducto", id);
		producto.asignarCodigo(codigo);
		return producto;
	}

	private static ProductoRequest solicitud(long idCategoria, List<CaracteristicaRequest> ficha) {
		return solicitud(idCategoria, ficha, null);
	}

	private static ProductoRequest solicitud(long idCategoria, List<CaracteristicaRequest> ficha, Boolean destacado) {
		return new ProductoRequest("  Taladro percutor 750 W ", " Bosch ", "  ", idCategoria, UnidadVenta.UNIDAD,
				new BigDecimal("420.00"), null, ficha, destacado);
	}

	// HU-07 RF-2, RF-3, RF-4
	@Test
	void creaActivoConCodigoDeLaCategoria() {
		categoria(3, "Herramientas eléctricas");
		guardaConNumero(1);

		ProductoResponse creado = servicio.crear(solicitud(3, List.of(new CaracteristicaRequest("Potencia", "750 W"))));

		assertThat(creado.codigo()).isEqualTo("HER-0001");
		assertThat(creado.nombre()).isEqualTo("Taladro percutor 750 W");
		assertThat(creado.marca()).isEqualTo("Bosch");
		assertThat(creado.descripcion()).isNull();
		assertThat(creado.activo()).isTrue();
		assertThat(creado.categoria()).isEqualTo("Herramientas eléctricas");
		assertThat(creado.fechaAlta()).isEqualTo(LocalDateTime.of(2026, 10, 10, 9, 0));
		assertThat(creado.ficha()).containsExactly(new CaracteristicaResponse("Potencia", "750 W"));
	}

	// HU-07 RF-4
	@Test
	void prefijoSinTildesYEnMayusculas() {
		categoria(4, "Jardín");
		guardaConNumero(7);

		assertThat(servicio.crear(solicitud(4, List.of())).codigo()).isEqualTo("JAR-0007");
	}

	// HU-07 RF-4: solo cuentan las letras; una categoría sin letras usa un prefijo fijo
	@Test
	void prefijoSoloConLetras() {
		assertThat(ProductoService.prefijo("  Ñandú 2")).isEqualTo("NAN");
		assertThat(ProductoService.prefijo("3D")).isEqualTo("D");
		assertThat(ProductoService.prefijo("123")).isEqualTo("PRD");
	}

	// HU-07 RF-11
	@Test
	void categoriaInexistenteAlCrear() {
		given(categoriaRepository.findConBloqueoByIdCategoria(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.crear(solicitud(99, List.of())))
				.isInstanceOf(CategoriaNoEncontradaException.class);
		then(productoRepository).should(never()).saveAndFlush(any());
	}

	// HU-07 RF-6: el mismo nombre, sin importar mayúsculas ni tildes
	@Test
	void fichaConNombresRepetidosSeRechaza() {
		categoria(3, "Herramientas");

		assertThatThrownBy(() -> servicio.crear(solicitud(3, List.of(new CaracteristicaRequest("Potencia", "750 W"),
				new CaracteristicaRequest(" POTÉNCIA ", "800 W")))))
				.isInstanceOfSatisfying(FichaInvalidaException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("FICHA_INVALIDA"));
		then(productoRepository).should(never()).saveAndFlush(any());
	}

	// HU-07 RF-6
	@Test
	void paresVaciosSeDescartan() {
		ProductoRequest conVacios = solicitud(3, java.util.Arrays.asList(new CaracteristicaRequest(" ", ""), null,
				new CaracteristicaRequest(" Mandril ", " 13 mm "), new CaracteristicaRequest(null, null)));

		assertThat(conVacios.ficha()).containsExactly(new CaracteristicaRequest("Mandril", "13 mm"));
	}

	// HU-07 RF-5
	@Test
	void editarCambiaLaCategoriaSinCambiarElCodigo() {
		Categoria herramientas = Categoria.crear("Herramientas", null);
		categoria(2, "Jardín");
		existente(9, herramientas, "HER-0009");

		ProductoResponse editado = servicio.editar(9L, solicitud(2, List.of()));

		assertThat(editado.codigo()).isEqualTo("HER-0009");
		assertThat(editado.categoria()).isEqualTo("Jardín");
		assertThat(editado.nombre()).isEqualTo("Taladro percutor 750 W");
	}

	// HU-09 RF-4: sin decirlo, un producto nuevo no es destacado; al editarlo se marca y se desmarca
	@Test
	void editarCambiaDestacado() {
		categoria(3, "Herramientas eléctricas");
		guardaConNumero(1);
		assertThat(servicio.crear(solicitud(3, List.of())).destacado()).isFalse();

		Producto taladro = existente(9, Categoria.crear("Herramientas", null), "HER-0009");

		assertThat(servicio.editar(9L, solicitud(3, List.of(), true)).destacado()).isTrue();
		assertThat(taladro.isDestacado()).isTrue();
		assertThat(servicio.editar(9L, solicitud(3, List.of(), false)).destacado()).isFalse();
	}

	// HU-08 RF-13: 12,5 m no se pueden volver unidades; 12 m sí
	@Test
	void unidadSinDecimalesConStockDecimalSeRechaza() {
		Categoria jardin = categoria(2, "Jardín");
		Producto manguera = Producto.crear("Manguera", null, null, BigDecimal.TEN, UnidadVenta.METRO, jardin,
				List.of(), LocalDateTime.ofInstant(AHORA, ZONA));
		ReflectionTestUtils.setField(manguera, "idProducto", 9L);
		manguera.asignarCodigo("JAR-0009");
		manguera.aplicarMovimiento(new BigDecimal("12.5"));
		given(productoRepository.findConBloqueoByIdProducto(9L)).willReturn(Optional.of(manguera));

		assertThatThrownBy(() -> servicio.editar(9L, solicitud(2, List.of())))
				.isInstanceOfSatisfying(UnidadIncompatibleConStockException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("UNIDAD_INCOMPATIBLE_CON_STOCK"));
		assertThat(manguera.getUnidad()).isEqualTo(UnidadVenta.METRO);

		manguera.aplicarMovimiento(new BigDecimal("12.00"));
		manguera.cambiarMinimo(new BigDecimal("1.5"));
		assertThatThrownBy(() -> servicio.editar(9L, solicitud(2, List.of())))
				.isInstanceOf(UnidadIncompatibleConStockException.class);

		manguera.cambiarMinimo(new BigDecimal("2"));
		assertThat(servicio.editar(9L, solicitud(2, List.of())).unidad()).isEqualTo(UnidadVenta.UNIDAD);
	}

	// HU-07 RF-11
	@Test
	void productoInexistente() {
		given(productoRepository.findConBloqueoByIdProducto(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarActivo(99L, false))
				.isInstanceOfSatisfying(ProductoNoEncontradoException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("PRODUCTO_NO_ENCONTRADO"));
	}

	// HU-07 RF-9
	@Test
	void desactivarYActivar() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009");

		assertThat(servicio.cambiarActivo(9L, false).activo()).isFalse();
		assertThat(servicio.cambiarActivo(9L, true).activo()).isTrue();
	}

	// HU-07 RF-7
	@Test
	void imagenSeAgregaAlFinal() {
		Producto producto = existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		producto.agregarImagen("a.png");
		given(almacen.guardar(PNG, FormatoImagen.PNG)).willReturn("b.png");

		assertThat(servicio.agregarImagen(9L, PNG).imagenes()).containsExactly("a.png", "b.png");
	}

	// HU-07 RF-7
	@Test
	void sextaImagenSeRechaza() {
		Producto producto = existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		List.of("1.png", "2.png", "3.png", "4.png", "5.png").forEach(producto::agregarImagen);

		assertThatThrownBy(() -> servicio.agregarImagen(9L, PNG))
				.isInstanceOfSatisfying(ProductoLlenoDeImagenesException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("PRODUCTO_LLENO_DE_IMAGENES"));
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-07 RF-7: un PDF con nombre de foto
	@Test
	void imagenInvalidaSeRechaza() {
		assertThatThrownBy(() -> servicio.agregarImagen(9L, "%PDF-1.7 no es una imagen".getBytes()))
				.isInstanceOf(ImagenInvalidaException.class);
		then(productoRepository).shouldHaveNoInteractions();
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-07 RF-8
	@Test
	void reordenarPoneLaElegidaComoPrincipal() {
		Producto producto = existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		List.of("a.png", "b.png", "c.png").forEach(producto::agregarImagen);

		assertThat(servicio.reordenar(9L, List.of("c.png", "a.png", "b.png")).imagenes())
				.containsExactly("c.png", "a.png", "b.png");
	}

	// HU-07 RF-8: de más, de menos, repetidas o ajenas
	@Test
	void reordenarConOtraListaSeRechaza() {
		Producto producto = existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		List.of("a.png", "b.png").forEach(producto::agregarImagen);

		for (List<String> orden : List.of(List.of("a.png"), List.of("a.png", "b.png", "c.png"),
				List.of("a.png", "a.png"), List.of("a.png", "x.png"))) {
			assertThatThrownBy(() -> servicio.reordenar(9L, orden))
					.isInstanceOf(OrdenDeImagenesInvalidoException.class);
		}
		assertThat(producto.getImagenes()).containsExactly("a.png", "b.png");
	}

	// HU-07 RF-8: el archivo se borra (sin transacción, enseguida)
	@Test
	void quitarImagenBorraElArchivo() {
		Producto producto = existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		List.of("a.png", "b.png").forEach(producto::agregarImagen);

		assertThat(servicio.quitarImagen(9L, "a.png").imagenes()).containsExactly("b.png");
		then(almacen).should().borrar("a.png");
	}

	// HU-07 RF-8: un nombre que no es del producto no toca ningún archivo
	@Test
	void quitarUnaImagenAjenaNoBorraNada() {
		Producto producto = existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		producto.agregarImagen("a.png");

		assertThat(servicio.quitarImagen(9L, "otra.png").imagenes()).containsExactly("a.png");
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-07 RF-7: las fotos siguen en 2 MB aunque multipart deje pasar los 3 MB de un modelo (HU-15)
	@Test
	void unaFotoDeMasDe2MbSigueRechazada() {
		byte[] grande = java.util.Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);

		assertThatThrownBy(() -> servicio.agregarImagen(9L, grande))
				.isInstanceOfSatisfying(ImagenDemasiadoGrandeException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("IMAGEN_DEMASIADO_GRANDE"));
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-15 RF-2
	@Test
	void subeElModeloYGuardaElPeso() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		byte[] modelo = FormatoModeloTest.glb(2048);
		given(almacen.guardar(modelo, "glb")).willReturn("m.glb");

		ProductoResponse respuesta = servicio.subirModelo(9L, modelo);

		assertThat(respuesta.modelo().archivo()).isEqualTo("m.glb");
		assertThat(respuesta.modelo().peso()).isEqualTo(2048);
		then(almacen).should(never()).borrar(any());
	}

	// HU-15 RF-2: como las fotos, también un producto inactivo
	@Test
	void aceptaUnProductoInactivo() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009").desactivar();
		given(almacen.guardar(any(), any(String.class))).willReturn("m.glb");

		assertThat(servicio.subirModelo(9L, FormatoModeloTest.glb(64)).modelo()).isNotNull();
	}

	// HU-15 RF-3: no se toca la base ni la carpeta
	@Test
	void rechazaLoQueNoEsGlb() {
		assertThatThrownBy(() -> servicio.subirModelo(9L, PNG))
				.isInstanceOfSatisfying(ModeloInvalidoException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("MODELO_INVALIDO"));
		then(productoRepository).shouldHaveNoInteractions();
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-15 RF-4: 3 MB justos se aceptan; un byte más, no
	@Test
	void rechazaMasDe3MbYAcepta3Justos() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009");
		given(almacen.guardar(any(), any(String.class))).willReturn("m.glb");
		int tresMb = 3 * 1024 * 1024;

		assertThat(servicio.subirModelo(9L, FormatoModeloTest.glb(tresMb)).modelo().peso()).isEqualTo(tresMb);
		assertThatThrownBy(() -> servicio.subirModelo(9L, FormatoModeloTest.glb(tresMb + 1)))
				.isInstanceOfSatisfying(ModeloDemasiadoGrandeException.class,
						ex -> assertThat(ex.getCodigo()).isEqualTo("MODELO_DEMASIADO_GRANDE"));
	}

	// HU-15 RF-5: el anterior se borra (sin transacción, enseguida)
	@Test
	void reemplazaYBorraElAnterior() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009").cambiarModelo("viejo.glb", 100);
		given(almacen.guardar(any(), any(String.class))).willReturn("nuevo.glb");

		assertThat(servicio.subirModelo(9L, FormatoModeloTest.glb(64)).modelo().archivo()).isEqualTo("nuevo.glb");
		then(almacen).should().borrar("viejo.glb");
	}

	// HU-15 RF-6
	@Test
	void quitaYBorraElArchivo() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009").cambiarModelo("m.glb", 100);

		assertThat(servicio.quitarModelo(9L).modelo()).isNull();
		then(almacen).should().borrar("m.glb");
	}

	// HU-15 RF-6
	@Test
	void quitarSinModeloNoCambiaNada() {
		existente(9, Categoria.crear("Jardín", null), "JAR-0009");

		assertThat(servicio.quitarModelo(9L).modelo()).isNull();
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-15 RF-7
	@Test
	void modeloDeUnProductoInexistente() {
		given(productoRepository.findConBloqueoByIdProducto(99L)).willReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.subirModelo(99L, FormatoModeloTest.glb(64)))
				.isInstanceOf(ProductoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.quitarModelo(99L)).isInstanceOf(ProductoNoEncontradoException.class);
		then(almacen).shouldHaveNoInteractions();
	}

	// HU-15 RF-8: solo el modelo actual del producto
	@Test
	void sirveSoloElModeloActual() {
		Producto producto = sinBloqueo(9, Categoria.crear("Jardín", null), "JAR-0009");
		producto.cambiarModelo("m.glb", 3);
		given(productoRepository.findById(9L)).willReturn(Optional.of(producto));
		given(almacen.leer("m.glb")).willReturn(Optional.of(new byte[] { 1, 2, 3 }));

		assertThat(servicio.modelo(9L, "m.glb")).hasValueSatisfying(bytes -> assertThat(bytes).hasSize(3));
		assertThat(servicio.modelo(9L, "otro.glb")).isEmpty();
	}

	// HU-15 RF-12
	@Test
	void elResumenDiceSiTieneModelo() {
		Producto producto = sinBloqueo(9, Categoria.crear("Jardín", null), "JAR-0009");
		assertThat(ProductoResumenResponse.from(producto).conModelo()).isFalse();

		producto.cambiarModelo("m.glb", 100);
		assertThat(ProductoResumenResponse.from(producto).conModelo()).isTrue();
	}

	// /code-review: una fila con modelo y sin peso (editada a mano) no rompe el producto
	@Test
	void unModeloSinPesoNoRompeLaRespuesta() {
		Producto producto = sinBloqueo(9, Categoria.crear("Jardín", null), "JAR-0009");
		ReflectionTestUtils.setField(producto, "modelo", "m.glb");

		assertThat(ProductoResponse.from(producto).modelo().peso()).isZero();
	}
}
