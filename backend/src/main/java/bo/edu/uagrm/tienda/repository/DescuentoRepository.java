package bo.edu.uagrm.tienda.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import bo.edu.uagrm.tienda.entity.Descuento;

public interface DescuentoRepository extends JpaRepository<Descuento, Long> {

	// HU-24 RF-10: los de hoy, con el primer y el último día incluidos. La tabla es chica: PreciosDeVenta la lee una
	// vez por respuesta en lugar de una consulta por producto
	@Query("select d from Descuento d where d.inicio <= :hoy and d.fin >= :hoy")
	List<Descuento> vigentes(@Param("hoy") LocalDate hoy);

	// HU-24 RF-2: los del mismo producto (o de la misma categoría) cuyas fechas se tocan aunque sea un día. Con left
	// join: un join implícito dejaría afuera los de la otra clase. sinId: el que se está editando (RF-4)
	@Query("""
			select d from Descuento d left join d.producto p left join d.categoria c
			where ((:idProducto is not null and p.idProducto = :idProducto)
					or (:idCategoria is not null and c.idCategoria = :idCategoria))
				and d.inicio <= :fin and d.fin >= :inicio
				and (:sinId is null or d.idDescuento <> :sinId)
			order by d.inicio
			""")
	List<Descuento> cruzados(@Param("idProducto") Long idProducto, @Param("idCategoria") Long idCategoria,
			@Param("inicio") LocalDate inicio, @Param("fin") LocalDate fin, @Param("sinId") Long sinId);

	// Al borrar una categoría sin productos (CategoriaService)
	void deleteByCategoria_IdCategoria(Long idCategoria);

	// HU-24 RF-3: vigentes, después programados y al final vencidos; dentro de cada grupo, los que empiezan más tarde
	// primero
	@EntityGraph(attributePaths = { "producto", "categoria" })
	@Query(value = """
			select d from Descuento d
			order by case when d.inicio <= :hoy and d.fin >= :hoy then 0 when d.inicio > :hoy then 1 else 2 end,
				d.inicio desc, d.idDescuento desc
			""", countQuery = "select count(d) from Descuento d")
	Page<Descuento> listar(@Param("hoy") LocalDate hoy, Pageable pagina);
}
