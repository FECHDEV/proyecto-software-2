package bo.edu.uagrm.tienda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Descuento;
import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.UnidadVenta;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.DescuentoRepository;

// HU-24 RF-8 a RF-10: el precio de hoy de cada producto, con el mayor descuento vigente del producto o de su categoría
@ExtendWith(MockitoExtension.class)
class PreciosDeVentaTest {

	// 18/11/2026 a las 11:00 en Bolivia
	private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneId.of("America/La_Paz"));
	private static final LocalDate HOY = LocalDate.of(2026, 11, 18);
	private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 18, 9, 0);

	@Mock
	private DescuentoRepository descuentoRepository;

	private PreciosDeVenta precios;
	private Categoria herramientas;
	private Categoria jardin;
	private Producto martillo;
	private Producto regadera;
	private Usuario admin;

	@BeforeEach
	void preparar() {
		precios = new PreciosDeVenta(descuentoRepository, RELOJ);
		herramientas = categoria("Herramientas", 1L);
		jardin = categoria("Jardín", 2L);
		martillo = producto("Martillo", "100.00", herramientas, 10L);
		regadera = producto("Regadera", "40.00", jardin, 20L);
		admin = Usuario.crearAdministrador("Ada", "Admin", "ada@mail.com", "$2a$10$hash", AHORA);
	}

	private static Categoria categoria(String nombre, Long id) {
		Categoria categoria = Categoria.crear(nombre, null);
		ReflectionTestUtils.setField(categoria, "idCategoria", id);
		return categoria;
	}

	private static Producto producto(String nombre, String precio, Categoria categoria, Long id) {
		Producto producto = Producto.crear(nombre, null, null, new BigDecimal(precio), UnidadVenta.UNIDAD, categoria,
				List.of(), AHORA);
		ReflectionTestUtils.setField(producto, "idProducto", id);
		return producto;
	}

	private Descuento deProducto(Producto producto, int porcentaje, LocalDate fin) {
		return Descuento.deProducto(producto, porcentaje, HOY.minusDays(1), fin, admin, AHORA);
	}

	private Descuento deCategoria(Categoria categoria, int porcentaje, LocalDate fin) {
		return Descuento.deCategoria(categoria, porcentaje, HOY.minusDays(1), fin, admin, AHORA);
	}

	// HU-24 RF-8
	@Test
	void sinDescuentosCobraElNormal() {
		given(descuentoRepository.vigentes(HOY)).willReturn(List.of());

		PrecioDeVenta precio = precios.para(List.of(martillo)).get(martillo.getIdProducto());

		assertThat(precio.cobrado()).isEqualByComparingTo("100.00");
		assertThat(precio.conDescuento()).isFalse();
	}

	// HU-24 RF-9: gana el mayor, venga del producto o de la categoría
	@Test
	void elDelProductoYElDeLaCategoriaGanaElMayor() {
		LocalDate fin = HOY.plusDays(5);
		given(descuentoRepository.vigentes(HOY)).willReturn(
				List.of(deProducto(martillo, 10, fin), deCategoria(herramientas, 25, fin), deProducto(regadera, 30, fin),
						deCategoria(jardin, 5, fin)));

		Map<Long, PrecioDeVenta> resultado = precios.para(List.of(martillo, regadera));

		assertThat(resultado.get(10L).descuento()).isEqualTo(25);
		assertThat(resultado.get(10L).cobrado()).isEqualByComparingTo("75.00");
		assertThat(resultado.get(20L).descuento()).isEqualTo(30);
		assertThat(resultado.get(20L).cobrado()).isEqualByComparingTo("28.00");
	}

	// Caso límite: dos del mismo producto (dos Administradores a la vez) gana el mayor; si empatan, el que termina
	// después, que es la validez del precio para buscadores (RF-14)
	@Test
	void dosDelMismoProductoGanaElMayor() {
		given(descuentoRepository.vigentes(HOY)).willReturn(List.of(deProducto(martillo, 10, HOY.plusDays(9)),
				deProducto(martillo, 20, HOY.plusDays(2)), deProducto(martillo, 20, HOY.plusDays(4))));

		PrecioDeVenta precio = precios.para(List.of(martillo)).get(martillo.getIdProducto());

		assertThat(precio.descuento()).isEqualTo(20);
		assertThat(precio.fin()).isEqualTo(HOY.plusDays(4));
	}

	// Caso límite: cuenta la categoría que el producto tiene hoy
	@Test
	void usaLaCategoriaActual() {
		given(descuentoRepository.vigentes(HOY)).willReturn(List.of(deCategoria(herramientas, 50, HOY.plusDays(3))));

		assertThat(precios.para(List.of(regadera)).get(regadera.getIdProducto()).conDescuento()).isFalse();
		assertThat(precios.para(List.of(martillo)).get(martillo.getIdProducto()).descuento()).isEqualTo(50);
	}

	// Una sola lectura de los vigentes por llamada, por más productos que tenga la página
	@Test
	void leeLosVigentesUnaSolaVez() {
		given(descuentoRepository.vigentes(HOY)).willReturn(List.of());

		precios.para(List.of(martillo, regadera));

		then(descuentoRepository).should(times(1)).vigentes(HOY);
	}
}
