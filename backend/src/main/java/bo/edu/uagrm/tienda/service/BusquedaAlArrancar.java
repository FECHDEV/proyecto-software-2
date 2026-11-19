package bo.edu.uagrm.tienda.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Producto;
import bo.edu.uagrm.tienda.entity.Usuario;
import bo.edu.uagrm.tienda.repository.PedidoRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import bo.edu.uagrm.tienda.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;

// Sin migraciones, la columna de búsqueda de HU-09 llega vacía a los productos que ya existían, y la de HU-17 a los
// pedidos: se completan al arrancar. Una venta del mostrador no tiene a quien retira y queda sin búsqueda (HU-23). La de la cuenta (HU-23 RF-25, buscar al comprador por nombre) llega vacía a las cuentas de antes. Después las mantienen el producto al crearse y editarse, el pedido al confirmarse y la cuenta al crearse y al cambiar sus datos
@Component
@RequiredArgsConstructor
public class BusquedaAlArrancar {

	private final ProductoRepository productoRepository;
	private final PedidoRepository pedidoRepository;
	private final UsuarioRepository usuarioRepository;

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void completar() {
		productoRepository.findByBusquedaIsNull().forEach(Producto::actualizarBusqueda);
		pedidoRepository.findByBusquedaRetiraIsNullAndNombreRetiraIsNotNull().forEach(Pedido::actualizarBusqueda);
		usuarioRepository.findByBusquedaIsNull().forEach(Usuario::actualizarBusqueda);
	}
}
