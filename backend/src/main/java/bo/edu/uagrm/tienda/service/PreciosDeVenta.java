package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.entity.Descuento;
import bo.edu.uagrm.tienda.entity.PrecioDeVenta;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.repository.DescuentoRepository;
import lombok.RequiredArgsConstructor;

// El único lugar donde un producto toma su precio con descuento (HU-24 RF-8 a RF-10). Lee los vigentes de hoy una sola
// vez por llamada: la tabla es chica y así una página del catálogo no hace una consulta por producto
@Service
@RequiredArgsConstructor
public class PreciosDeVenta {

	// RF-9: gana el mayor porcentaje; si empatan, el que termina después (su fin es la validez del JSON-LD, RF-14)
	private static final Comparator<Descuento> MEJOR = Comparator.comparingInt(Descuento::getPorcentaje)
			.thenComparing(Descuento::getFin);

	private final DescuentoRepository descuentoRepository;
	private final Clock clock;

	@Transactional(readOnly = true)
	public Map<Long, PrecioDeVenta> para(Collection<Producto> productos) {
		if (productos.isEmpty()) {
			return Map.of();
		}
		LocalDate hoy = LocalDate.now(clock);
		Map<Long, Descuento> porProducto = new HashMap<>();
		Map<Long, Descuento> porCategoria = new HashMap<>();
		BinaryOperator<Descuento> mejor = BinaryOperator.maxBy(MEJOR);
		for (Descuento descuento : descuentoRepository.vigentes(hoy)) {
			if (descuento.getProducto() != null) {
				porProducto.merge(descuento.getProducto().getIdProducto(), descuento, mejor);
			} else {
				porCategoria.merge(descuento.getCategoria().getIdCategoria(), descuento, mejor);
			}
		}
		Map<Long, PrecioDeVenta> precios = new HashMap<>();
		for (Producto producto : productos) {
			// El propio y el de la categoría que tiene hoy (caso límite: cambió de categoría)
			Descuento ganador = Stream.of(porProducto.get(producto.getIdProducto()),
					porCategoria.get(producto.getCategoria().getIdCategoria())).filter(Objects::nonNull).max(MEJOR)
					.orElse(null);
			precios.put(producto.getIdProducto(), ganador == null ? PrecioDeVenta.sinDescuento(producto.getPrecio())
					: PrecioDeVenta.de(producto.getPrecio(), ganador.getPorcentaje(), ganador.getFin()));
		}
		return precios;
	}
}
