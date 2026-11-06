package bo.edu.uagrm.tienda.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.Canal;
import bo.edu.uagrm.tienda.entity.Cobro;
import bo.edu.uagrm.tienda.entity.EstadoCobro;
import jakarta.persistence.LockModeType;

public interface CobroRepository extends JpaRepository<Cobro, Long> {

	// El que se muestra y el que decide si el pedido vence (HU-13 RF-2, RF-7). Sin bloqueo: se lee con el cliente ya
	// bloqueado, y todo el que escribe un cobro bloquea antes al cliente. Un FOR UPDATE sobre un pedido sin cobros
	// bloquearía el hueco del índice (decisiones.md → Pedidos)
	Optional<Cobro> findFirstByPedido_IdPedidoAndEstado(Long idPedido, EstadoCobro estado);

	// Con el cliente y el pedido ya bloqueados; el cobro existe, así que no bloquea huecos (RF-11)
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Cobro> findConBloqueoByIdCobro(Long idCobro);

	// Lo necesario para consultar al sistema de pago, sin cargar el cobro: el que se bloquea después tiene que salir
	// de la base. Vacío si ya se procesó (RF-11)
	@Query("""
			select new bo.edu.uagrm.tienda.repository.CobroRepository$CobroAConciliar(
				c.idCobro, c.referencia, p.idPedido, u.idUsuario, p.canal)
			from Cobro c join c.pedido p left join p.usuario u where c.idCobro = :idCobro and c.estado = bo.edu.uagrm.tienda.entity.EstadoCobro.PENDIENTE""")
	Optional<CobroAConciliar> datosParaConciliar(@Param("idCobro") Long idCobro);

	// El aviso del sistema de pago llega con nuestra referencia (RF-10)
	@Query("select c.idCobro from Cobro c where c.referencia = :referencia")
	Optional<Long> idPorReferencia(@Param("referencia") String referencia);

	// La tarea de cada minuto (RF-8): la deuda vence por día, así que se mira hasta 24 horas
	@Query("""
			select c.idCobro from Cobro c
			where c.estado = bo.edu.uagrm.tienda.entity.EstadoCobro.PENDIENTE and c.generado >= :desde
			order by c.idCobro""")
	List<Long> idsPorConciliar(@Param("desde") LocalDateTime desde);

	// idUsuario es nulo en una venta del mostrador a consumidor final (HU-23)
	record CobroAConciliar(Long idCobro, String referencia, Long idPedido, Long idUsuario, Canal canal) {

		public boolean enTienda() {
			return canal == Canal.TIENDA;
		}
	}
}
