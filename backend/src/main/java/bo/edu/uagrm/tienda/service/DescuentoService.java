package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.DescuentoCambioRequest;
import bo.edu.uagrm.tienda.dto.DescuentoGuardadoResponse;
import bo.edu.uagrm.tienda.dto.DescuentoRequest;
import bo.edu.uagrm.tienda.dto.DescuentoResponse;
import bo.edu.uagrm.tienda.dto.PaginaResponse;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.entity.Descuento;
import bo.edu.uagrm.tienda.entity.EstadoDescuento;
import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException;
import bo.edu.uagrm.tienda.exception.CamposInvalidosException.CampoInvalido;
import bo.edu.uagrm.tienda.exception.DescuentoNoEditableException;
import bo.edu.uagrm.tienda.exception.DescuentoNoEncontradoException;
import bo.edu.uagrm.tienda.exception.DescuentoSolapadoException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.DescuentoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// La gestión de descuentos del Administrador (HU-24 RF-1 a RF-6); quién llega lo decide SecurityConfig
// (/api/gestion/**). El solapamiento se valida sin bloquear filas: si dos Administradores guardaran a la vez dos que se
// cruzan, PreciosDeVenta aplica el mayor y el dinero no se rompe (decisiones.md → Descuentos)
@Service
@RequiredArgsConstructor
public class DescuentoService {

	static final int POR_PAGINA = 20;
	// RF-1: el fin, hasta 366 días después del inicio
	static final int DURACION_MAXIMA = 366;
	// RF-6: cuántos productos bajo el costo se nombran
	static final int AVISOS_DE_COSTO = 10;
	private static final int ULTIMO_ANIO = 9999;

	private final DescuentoRepository descuentoRepository;
	private final ProductoRepository productoRepository;
	private final CategoriaRepository categoriaRepository;
	private final UsuarioRepository usuarioRepository;
	private final Clock clock;

	// RF-3: vigentes, programados y vencidos, de a 20
	@Transactional(readOnly = true)
	public PaginaResponse<DescuentoResponse> listar(int pagina) {
		LocalDate hoy = LocalDate.now(clock);
		return PaginaResponse.from(
				descuentoRepository.listar(hoy, PageRequest.of(Busquedas.paginaValida(pagina, POR_PAGINA), POR_PAGINA)),
				descuento -> DescuentoResponse.from(descuento, hoy));
	}

	// RF-1, RF-2, RF-6
	@Transactional
	public DescuentoGuardadoResponse crear(DescuentoRequest solicitud, Long idAdministrador) {
		LocalDate hoy = LocalDate.now(clock);
		if ((solicitud.idProducto() == null) == (solicitud.idCategoria() == null)) {
			throw campoInvalido("idProducto", "elija un producto o una categoría, uno solo");
		}
		validarFechas(solicitud.inicio(), solicitud.fin(), hoy);
		LocalDateTime ahora = LocalDateTime.now(clock);
		Descuento descuento;
		if (solicitud.idProducto() != null) {
			Producto producto = productoRepository.findById(solicitud.idProducto())
					.orElseThrow(() -> campoInvalido("idProducto", "no existe"));
			descuento = Descuento.deProducto(producto, solicitud.porcentaje(), solicitud.inicio(), solicitud.fin(),
					usuarioRepository.getReferenceById(idAdministrador), ahora);
		} else {
			// Con el bloqueo de la categoría, como CategoriaService al borrarla: un descuento no llega mientras se borra
			// (/code-review)
			Categoria categoria = categoriaRepository.findConBloqueoByIdCategoria(solicitud.idCategoria())
					.orElseThrow(() -> campoInvalido("idCategoria", "no existe"));
			descuento = Descuento.deCategoria(categoria, solicitud.porcentaje(), solicitud.inicio(), solicitud.fin(),
					usuarioRepository.getReferenceById(idAdministrador), ahora);
		}
		exigirSinCruce(descuento, null);
		descuentoRepository.save(descuento);
		return guardado(descuento, hoy);
	}

	// RF-4: solo un programado; el producto o la categoría no cambian
	@Transactional
	public DescuentoGuardadoResponse cambiar(Long idDescuento, DescuentoCambioRequest solicitud) {
		LocalDate hoy = LocalDate.now(clock);
		Descuento descuento = buscar(idDescuento);
		exigirEstado(descuento, hoy, EstadoDescuento.PROGRAMADO, "Solo se edita un descuento que todavía no empezó.");
		validarFechas(solicitud.inicio(), solicitud.fin(), hoy);
		descuento.cambiar(solicitud.porcentaje(), solicitud.inicio(), solicitud.fin());
		exigirSinCruce(descuento, idDescuento);
		return guardado(descuento, hoy);
	}

	// RF-4
	@Transactional
	public void borrar(Long idDescuento) {
		Descuento descuento = buscar(idDescuento);
		exigirEstado(descuento, LocalDate.now(clock), EstadoDescuento.PROGRAMADO,
				"Solo se borra un descuento que todavía no empezó; uno vigente se termina.");
		descuentoRepository.delete(descuento);
	}

	// RF-5: deja de aplicarse en el acto. Si empezó hoy se borra, porque el fin no puede quedar antes del inicio; lo
	// vendido no cambia, porque la línea del pedido guarda su precio y su porcentaje (RF-15)
	@Transactional
	public void terminar(Long idDescuento) {
		LocalDate hoy = LocalDate.now(clock);
		Descuento descuento = buscar(idDescuento);
		exigirEstado(descuento, hoy, EstadoDescuento.VIGENTE, "Solo se termina un descuento vigente.");
		if (descuento.getInicio().equals(hoy)) {
			descuentoRepository.delete(descuento);
		} else {
			descuento.terminar(hoy);
		}
	}

	private Descuento buscar(Long idDescuento) {
		return descuentoRepository.findById(idDescuento).orElseThrow(DescuentoNoEncontradoException::new);
	}

	private static void exigirEstado(Descuento descuento, LocalDate hoy, EstadoDescuento estado, String detalle) {
		if (descuento.estado(hoy) != estado) {
			throw new DescuentoNoEditableException(detalle);
		}
	}

	// RF-1: desde hoy, el fin desde el inicio y hasta 366 días después. Un año imposible (después de 9999, lo que
	// guarda la columna DATE de MySQL) es un dato basura, no un 500 (/code-review)
	private static void validarFechas(LocalDate inicio, LocalDate fin, LocalDate hoy) {
		if (inicio.getYear() > ULTIMO_ANIO || fin.getYear() > ULTIMO_ANIO) {
			throw campoInvalido(inicio.getYear() > ULTIMO_ANIO ? "inicio" : "fin", "no es una fecha válida");
		}
		if (inicio.isBefore(hoy)) {
			throw campoInvalido("inicio", "no puede ser anterior a hoy");
		}
		if (fin.isBefore(inicio)) {
			throw campoInvalido("fin", "no puede ser anterior al inicio");
		}
		if (fin.isAfter(inicio.plusDays(DURACION_MAXIMA))) {
			throw campoInvalido("fin", "puede ser hasta " + DURACION_MAXIMA + " días después del inicio");
		}
	}

	// RF-2: con el mismo producto o la misma categoría, sin contarse a sí mismo al editar
	private void exigirSinCruce(Descuento descuento, Long sinId) {
		Long idProducto = descuento.getProducto() == null ? null : descuento.getProducto().getIdProducto();
		Long idCategoria = descuento.getCategoria() == null ? null : descuento.getCategoria().getIdCategoria();
		List<Descuento> cruzados = descuentoRepository.cruzados(idProducto, idCategoria, descuento.getInicio(),
				descuento.getFin(), sinId);
		if (!cruzados.isEmpty()) {
			throw new DescuentoSolapadoException(cruzados.getFirst());
		}
	}

	// RF-6: los productos activos a los que se aplica y que con este porcentaje quedan por debajo de su costo; se
	// nombran hasta 10 y se dice cuántos son. Con este porcentaje: si otro mayor se aplica a la vez (RF-9), ese avisó al
	// guardarse
	private DescuentoGuardadoResponse guardado(Descuento descuento, LocalDate hoy) {
		Producto delDescuento = descuento.getProducto();
		List<Producto> productos = delDescuento != null ? (delDescuento.isActivo() ? List.of(delDescuento) : List.of())
				: productoRepository.findByCategoria_IdCategoriaAndActivoTrue(descuento.getCategoria().getIdCategoria());
		List<String> bajoElCosto = productos.stream()
				.filter(producto -> producto.getCosto() != null && PrecioDeVenta
						.de(producto.getPrecio(), descuento.getPorcentaje(), descuento.getFin()).cobrado()
						.compareTo(producto.getCosto()) < 0)
				.map(Producto::getNombre).toList();
		return new DescuentoGuardadoResponse(DescuentoResponse.from(descuento, hoy),
				bajoElCosto.stream().limit(AVISOS_DE_COSTO).toList(), bajoElCosto.size());
	}

	private static CamposInvalidosException campoInvalido(String campo, String mensaje) {
		return new CamposInvalidosException(List.of(new CampoInvalido(campo, mensaje)));
	}
}
