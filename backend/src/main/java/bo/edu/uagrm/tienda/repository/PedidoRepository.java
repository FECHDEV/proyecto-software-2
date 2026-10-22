package bo.edu.uagrm.tienda.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import jakarta.persistence.LockModeType;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

	// El de otro cliente no aparece: responde igual que uno que no existe (HU-12 RF-13)
	Optional<Pedido> findByIdPedidoAndUsuario_IdUsuario(Long idPedido, Long idUsuario);

	// Cancelar y vencer revisan el estado con el pedido bloqueado: el que llega segundo no libera dos veces (RF-12)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Pedido> findConBloqueoByIdPedidoAndUsuario_IdUsuario(Long idPedido, Long idUsuario);

	// El cliente de un pedido sin cargarlo, para bloquear antes al cliente (HU-17, DespachoService.entregar)
	@Query("select p.usuario.idUsuario from Pedido p where p.idPedido = :idPedido")
	Optional<Long> idClienteDelPedido(@Param("idPedido") Long idPedido);

	// Los pendientes del cliente al confirmar, con el cliente ya bloqueado (RF-5)
	List<Pedido> findByUsuario_IdUsuarioAndEstadoOrderByIdPedido(Long idUsuario, EstadoPedido estado);

	// Los pendientes del cliente, para vencerlos o rechazar otra confirmación con el cliente ya bloqueado (RF-5, RF-10)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<Pedido> findConBloqueoByUsuario_IdUsuarioAndEstadoOrderByIdPedido(Long idUsuario, EstadoPedido estado);

	// Si hay que vencer algo al leer el carrito o un pedido, sin cargar ni bloquear el pedido (RF-10)
	boolean existsByUsuario_IdUsuarioAndEstadoAndVenceLessThanEqual(Long idUsuario, EstadoPedido estado,
			LocalDateTime ahora);

	// Los que ya cumplieron la hora, para la tarea programada (RF-10): solo los ids, y cada uno vence en su transacción
	@Query("""
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$PedidoAVencer(p.idPedido, p.usuario.idUsuario)
			from Pedido p where p.estado = :estado and p.vence <= :ahora order by p.vence, p.idPedido""")
	List<PedidoAVencer> findByEstadoAndVenceLessThanEqual(@Param("estado") EstadoPedido estado,
			@Param("ahora") LocalDateTime ahora);

	// «Mis pedidos» (HU-14 RF-1): una página del Cliente, el más nuevo primero, con la cantidad de líneas contada en
	// la misma consulta (sin cargar las líneas de cada pedido)
	@Query(value = """
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$FilaDePedido(
				p.idPedido, p.estado, p.confirmado, size(p.lineas), p.total, p.vence)
			from Pedido p where p.usuario.idUsuario = :idUsuario
			order by p.confirmado desc, p.idPedido desc""",
			countQuery = "select count(p) from Pedido p where p.usuario.idUsuario = :idUsuario")
	Page<FilaDePedido> misPedidos(@Param("idUsuario") Long idUsuario, Pageable pagina);

	// La lista del personal (HU-17 RF-1 a RF-3): todos los clientes, con un estado o todos (null) y, si hay texto, por
	// el nombre de quien retira (patrón LIKE ya normalizado) o por el número. El orden lo pone quien llama
	@Query(value = """
			select new bo.edu.uagrm.tienda.repository.PedidoRepository$FilaDeDespacho(
				p.idPedido, p.estado, p.confirmado, p.pagado, p.nombreRetira, size(p.lineas), p.total)
			from Pedido p
			where (:estado is null or p.estado = :estado)
			and (:patron is null or p.busquedaRetira like :patron escape '!' or p.idPedido = :numero)""",
			countQuery = """
					select count(p) from Pedido p
					where (:estado is null or p.estado = :estado)
					and (:patron is null or p.busquedaRetira like :patron escape '!' or p.idPedido = :numero)""")
	Page<FilaDeDespacho> despacho(@Param("estado") EstadoPedido estado, @Param("patron") String patron,
			@Param("numero") Long numero, Pageable pagina);

	// Los pedidos de antes de HU-17, para BusquedaAlArrancar
	List<Pedido> findByBusquedaRetiraIsNull();

	record FilaDeDespacho(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, LocalDateTime pagado,
			String nombreRetira, int cantidadProductos, BigDecimal total) {
	}

	record FilaDePedido(Long idPedido, EstadoPedido estado, LocalDateTime confirmado, int cantidadProductos,
			BigDecimal total, LocalDateTime vence) {
	}

	record PedidoAVencer(Long idPedido, Long idUsuario) {
	}
}
