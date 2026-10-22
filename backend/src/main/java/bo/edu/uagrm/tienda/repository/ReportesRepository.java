package bo.edu.uagrm.tienda.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.dto.reportes.CategoriaFila;
import bo.edu.uagrm.tienda.dto.reportes.CompraFila;
import bo.edu.uagrm.tienda.dto.reportes.EstadoFila;
import bo.edu.uagrm.tienda.dto.reportes.LineasFila;
import bo.edu.uagrm.tienda.dto.reportes.PerdidoFila;
import bo.edu.uagrm.tienda.dto.reportes.UltimaVentaFila;
import bo.edu.uagrm.tienda.dto.reportes.VentaFila;
import bo.edu.uagrm.tienda.entity.EstadoCuenta;
import bo.edu.uagrm.tienda.entity.EstadoPedido;
import bo.edu.uagrm.tienda.entity.Pedido;
import bo.edu.uagrm.tienda.entity.Rol;
import bo.edu.uagrm.tienda.entity.TipoCliente;

// Las sumas de los reportes (HU-18). La base agrupa; ReportesService arma las secciones en Java, así el mismo código
// vale en H2 y en MySQL (decisiones del plan). Un tipo nulo no filtra. El fin del período queda afuera
public interface ReportesRepository extends Repository<Pedido, Long> {

	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.VentaFila(p.idPedido, p.pagado, p.total, u.tipoCliente)
			from Pedido p join p.usuario u
			where p.estado in :estados and p.pagado >= :inicio and p.pagado < :fin
				and (:tipo is null or u.tipoCliente = :tipo)""")
	List<VentaFila> pagados(@Param("estados") Collection<EstadoPedido> estados, @Param("inicio") LocalDateTime inicio,
			@Param("fin") LocalDateTime fin, @Param("tipo") TipoCliente tipo);

	// El margen solo con las líneas que guardaron costo (RF-8): lo demás va a sinCosto
	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.LineasFila(l.producto.idProducto, sum(l.cantidad), sum(l.subtotal),
				sum(case when l.costo is not null then l.subtotal - l.costo * l.cantidad else cast(0 as BigDecimal) end),
				sum(case when l.costo is null then l.subtotal else cast(0 as BigDecimal) end))
			from LineaPedido l join l.pedido p join p.usuario u
			where p.estado in :estados and p.pagado >= :inicio and p.pagado < :fin
				and (:tipo is null or u.tipoCliente = :tipo)
			group by l.producto.idProducto""")
	List<LineasFila> lineasPorProducto(@Param("estados") Collection<EstadoPedido> estados,
			@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin, @Param("tipo") TipoCliente tipo);

	// La categoría actual del producto (RF-14), de mayor a menor importe
	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.CategoriaFila(c.nombre, sum(l.subtotal), count(distinct p.idPedido),
				sum(case when l.costo is not null then l.subtotal - l.costo * l.cantidad else cast(0 as BigDecimal) end),
				sum(case when l.costo is null then l.subtotal else cast(0 as BigDecimal) end))
			from LineaPedido l join l.pedido p join p.usuario u join l.producto pr join pr.categoria c
			where p.estado in :estados and p.pagado >= :inicio and p.pagado < :fin
				and (:tipo is null or u.tipoCliente = :tipo)
			group by c.idCategoria, c.nombre
			order by sum(l.subtotal) desc, c.nombre""")
	List<CategoriaFila> porCategoria(@Param("estados") Collection<EstadoPedido> estados,
			@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin, @Param("tipo") TipoCliente tipo);

	// Por la fecha de confirmación, no la del pago: un pedido perdido no tiene pago (RF-17)
	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.EstadoFila(p.estado, count(p), sum(p.total))
			from Pedido p join p.usuario u
			where p.confirmado >= :inicio and p.confirmado < :fin and (:tipo is null or u.tipoCliente = :tipo)
			group by p.estado""")
	List<EstadoFila> confirmadosPorEstado(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin,
			@Param("tipo") TipoCliente tipo);

	// RF-18: por la fecha de confirmación, como los perdidos del resumen
	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.PerdidoFila(l.producto.idProducto, sum(l.cantidad), sum(l.subtotal))
			from LineaPedido l join l.pedido p join p.usuario u
			where p.estado in :estados and p.confirmado >= :inicio and p.confirmado < :fin
				and (:tipo is null or u.tipoCliente = :tipo)
			group by l.producto.idProducto""")
	List<PerdidoFila> perdidosPorProducto(@Param("estados") Collection<EstadoPedido> estados,
			@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin, @Param("tipo") TipoCliente tipo);

	// RF-13: la última venta de cada producto que se vendió alguna vez
	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.UltimaVentaFila(l.producto.idProducto, max(p.pagado))
			from LineaPedido l join l.pedido p
			where p.estado in :estados
			group by l.producto.idProducto""")
	List<UltimaVentaFila> ultimasVentas(@Param("estados") Collection<EstadoPedido> estados);

	// RF-16: las cuentas activas del rol dado que compraron alguna vez, pero no desde el límite
	@Query("""
			select new bo.edu.uagrm.tienda.dto.reportes.CompraFila(u.idUsuario, max(p.pagado), count(p), sum(p.total))
			from Pedido p join p.usuario u
			where p.estado in :estados and u.rol = :rol and u.estado = :cuenta
				and (:tipo is null or u.tipoCliente = :tipo)
			group by u.idUsuario
			having max(p.pagado) <= :limite""")
	List<CompraFila> comprasHasta(@Param("estados") Collection<EstadoPedido> estados, @Param("rol") Rol rol,
			@Param("cuenta") EstadoCuenta cuenta, @Param("tipo") TipoCliente tipo,
			@Param("limite") LocalDateTime limite);
}
