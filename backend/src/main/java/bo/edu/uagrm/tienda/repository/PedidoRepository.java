package bo.edu.uagrm.tienda.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.Canal;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import jakarta.persistence.LockModeType;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	// El de otro cliente no aparece: responde igual que uno que no existe (HU-12 RF-13). Lo que el Cliente hace con su
	// pedido (pagarlo, consultarlo, cancelarlo) es solo en línea: una venta del mostrador la maneja el personal (HU-23)
	Optional<Pedido> findByIdPedidoAndUsuario_IdUsuarioAndCanal(Long idPedido, Long idUsuario, Canal canal);

	// Lo que el Cliente ve de un pedido suyo (HU-14, HU-23 RF-17): los de en línea, y del mostrador solo los ya pagados
	@Query("""
			select p from Pedido p where p.idPedido = :idPedido and p.usuario.idUsuario = :idUsuario
			and (p.canal = bo.edu.uagrm.tienda.entity.Canal.EN_LINEA or p.estado in :visibles)""")
	Optional<Pedido> visibleParaElCliente(@Param("idPedido") Long idPedido, @Param("idUsuario") Long idUsuario,
			@Param("visibles") Collection<EstadoPedido> visibles);

	// Cancelar y vencer revisan el estado con el pedido bloqueado: el que llega segundo no libera dos veces (RF-12).
	// La conciliación y la entrega de un pedido en línea lo bloquean después del cliente
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Pedido> findConBloqueoByIdPedidoAndUsuario_IdUsuario(Long idPedido, Long idUsuario);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Pedido> findConBloqueoByIdPedidoAndUsuario_IdUsuarioAndCanal(Long idPedido, Long idUsuario, Canal canal);

	// Una venta del mostrador se bloquea primero ella, sin el cliente (HU-23; decisiones.md → Venta en tienda)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Pedido> findConBloqueoByIdPedido(Long idPedido);

	// El canal y el cliente de un pedido sin cargarlo, para bloquear antes al cliente en línea (HU-17,
	// DespachoService.entregar); una venta del mostrador se bloquea ella primero y puede no tener cliente (HU-23)
	@Query("""
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$DuenoDelPedido(p.canal, u.idUsuario)
			from Pedido p left join p.usuario u where p.idPedido = :idPedido""")
	Optional<DuenoDelPedido> duenoDelPedido(@Param("idPedido") Long idPedido);

	record DuenoDelPedido(Canal canal, Long idUsuario) {
	}

	// Los pendientes del cliente al confirmar, con el cliente ya bloqueado (RF-5). Siempre con el canal EN_LINEA: una
	// venta del mostrador a su nombre no es su pedido pendiente (HU-23)
	List<Pedido> findByUsuario_IdUsuarioAndCanalAndEstadoOrderByIdPedido(Long idUsuario, Canal canal,
			EstadoPedido estado);

	// Los pendientes del cliente, para vencerlos o rechazar otra confirmación con el cliente ya bloqueado (RF-5, RF-10)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<Pedido> findConBloqueoByUsuario_IdUsuarioAndCanalAndEstadoOrderByIdPedido(Long idUsuario, Canal canal,
			EstadoPedido estado);

	// Si hay que vencer algo al leer el carrito o un pedido, sin cargar ni bloquear el pedido (RF-10)
	boolean existsByUsuario_IdUsuarioAndCanalAndEstadoAndVenceLessThanEqual(Long idUsuario, Canal canal,
			EstadoPedido estado, LocalDateTime ahora);

	// Los que ya cumplieron la hora, para la tarea programada (RF-10): solo los ids, y cada uno vence en su transacción.
	// left join: una venta del mostrador puede no tener cliente (HU-23)
	@Query("""
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$PedidoAVencer(p.idPedido, u.idUsuario, p.canal)
			from Pedido p left join p.usuario u
			where p.estado = :estado and p.vence <= :ahora order by p.vence, p.idPedido""")
	List<PedidoAVencer> findByEstadoAndVenceLessThanEqual(@Param("estado") EstadoPedido estado,
			@Param("ahora") LocalDateTime ahora);

	// «Mis pedidos» (HU-14 RF-1): una página del Cliente, el más nuevo primero, con la cantidad de líneas contada en
	// la misma consulta (sin cargar las líneas de cada pedido). Del mostrador, solo las ya pagadas (HU-23 RF-17)
	@Query(value = """
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$FilaDePedido(
				p.idPedido, p.estado, p.confirmado, size(p.lineas), p.total, p.vence, p.canal)
			from Pedido p where p.usuario.idUsuario = :idUsuario
			and (p.canal = bo.edu.uagrm.tienda.entity.Canal.EN_LINEA or p.estado in :visibles)
			order by p.confirmado desc, p.idPedido desc""",
			countQuery = """
					select count(p) from Pedido p where p.usuario.idUsuario = :idUsuario
					and (p.canal = bo.edu.uagrm.tienda.entity.Canal.EN_LINEA or p.estado in :visibles)""")
	Page<FilaDePedido> misPedidos(@Param("idUsuario") Long idUsuario,
			@Param("visibles") Collection<EstadoPedido> visibles, Pageable pagina);

	// La lista del personal (HU-17 RF-1 a RF-3): todos los clientes, con un estado o todos (null), un canal o todos
	// (null, HU-23 RF-16) y, si hay texto, por el nombre de quien retira (patrón LIKE ya normalizado) o por el número.
	// El orden lo pone quien llama
	@Query(value = """
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$FilaDeDespacho(
				p.idPedido, p.estado, p.confirmado, p.pagado, p.nombreRetira, size(p.lineas), p.total, p.canal)
			from Pedido p
			where (:estado is null or p.estado = :estado)
			and (:canal is null or p.canal = :canal)
			and (:patron is null or p.busquedaRetira like :patron escape '!' or p.idPedido = :numero)""",
			countQuery = """
					select count(p) from Pedido p
					where (:estado is null or p.estado = :estado)
					and (:canal is null or p.canal = :canal)
					and (:patron is null or p.busquedaRetira like :patron escape '!' or p.idPedido = :numero)""")
	Page<FilaDeDespacho> despacho(@Param("estado") EstadoPedido estado, @Param("canal") Canal canal,
			@Param("patron") String patron, @Param("numero") Long numero, Pageable pagina);

	// Los pedidos de antes de HU-17, para BusquedaAlArrancar
	List<Pedido> findByBusquedaRetiraIsNullAndNombreRetiraIsNotNull();

	// Lo que de cada producto sigue en el estante por un pedido: reservado (pendiente de pago) o pagado sin entregar
	// (HU-08 RF-15). Los productos sin pedidos abiertos no vienen
	@Query("""
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$Comprometido(l.producto.idProducto, sum(l.cantidad))
			from LineaPedido l
			where l.pedido.estado in (bo.edu.uagrm.tienda.entity.EstadoPedido.PENDIENTE_PAGO,
				bo.edu.uagrm.tienda.entity.EstadoPedido.PAGADO)
			and l.producto.idProducto in :idsProductos
			group by l.producto.idProducto""")
	List<Comprometido> comprometidos(@Param("idsProductos") Collection<Long> idsProductos);

	record Comprometido(Long idProducto, BigDecimal cantidad) {
	}

	// Las cantidades de un producto en pedidos abiertos, una por línea: al cambiar la unidad, cada una tiene que
	// admitirla (HU-08 RF-19)
	@Query("""
			select l.cantidad from LineaPedido l
			where l.pedido.estado in (bo.edu.uagrm.tienda.entity.EstadoPedido.PENDIENTE_PAGO,
				bo.edu.uagrm.tienda.entity.EstadoPedido.PAGADO)
			and l.producto.idProducto = :idProducto""")
	List<BigDecimal> cantidadesAbiertas(@Param("idProducto") Long idProducto);

	record FilaDeDespacho(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, LocalDateTime pagado,
			String nombreRetira, int cantidadProductos, BigDecimal total, Canal canal) {
	}

	record FilaDePedido(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, int cantidadProductos,
			BigDecimal total, LocalDateTime vence, Canal canal) {
	}

	// idUsuario es nulo en una venta del mostrador a consumidor final
	record PedidoAVencer(Long idPedido, Long idUsuario, Canal canal) {
	}
}
