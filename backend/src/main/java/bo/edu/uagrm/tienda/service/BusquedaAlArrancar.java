package bo.edu.uagrm.tienda.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

// Sin migraciones, la columna de búsqueda de HU-09 llega vacía a los productos que ya existían, y la de HU-17 a los
// pedidos: se completan al arrancar. Después las mantienen el producto al crearse y editarse, y el pedido al confirmarse
@Component
@RequiredArgsConstructor
public class BusquedaAlArrancar {

	private final ProductoRepository productoRepository;
	private final PedidoRepository pedidoRepository;

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void completar() {
		productoRepository.findByBusquedaIsNull().forEach(Producto::actualizarBusqueda);
		pedidoRepository.findByBusquedaRetiraIsNull().forEach(Pedido::actualizarBusqueda);
	}
}
