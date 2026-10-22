package bo.edu.uagrm.tienda.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import bo.edu.uagrm.tienda.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

	// Los que se pueden vender, para los reportes (HU-18 RF-12)
	List<Producto> findByActivoTrue();

	// texto llega ya en minúsculas, con los comodines escapados con '!' y entre '%'. La categoría viene en la misma
	// consulta: la lista muestra su nombre
	@EntityGraph(attributePaths = "categoria")
	@Query("""
			select p from Producto p
			where (:idCategoria is null or p.categoria.idCategoria = :idCategoria)
			  and (:activo is null or p.activo = :activo)
			  and (:texto is null or lower(p.nombre) like :texto escape '!' or lower(p.codigo) like :texto escape '!'
			    or lower(p.marca) like :texto escape '!')
			""")
	Page<Producto> buscar(@Param("idCategoria") Long idCategoria, @Param("activo") Boolean activo,
			@Param("texto") String texto, Pageable paginacion);

	// El inventario (HU-08): activos e inactivos; texto como en buscar, en el nombre o el código
	@EntityGraph(attributePaths = "categoria")
	@Query("""
			select p from Producto p
			where (:idCategoria is null or p.categoria.idCategoria = :idCategoria)
			  and (:activo is null or p.activo = :activo)
			  and (:soloBajo = false or (p.stockMinimo is not null and p.stock <= p.stockMinimo))
			  and (:texto is null or lower(p.nombre) like :texto escape '!' or lower(p.codigo) like :texto escape '!')
			""")
	Page<Producto> inventario(@Param("texto") String texto, @Param("idCategoria") Long idCategoria,
			@Param("activo") Boolean activo, @Param("soloBajo") boolean soloBajo, Pageable paginacion);

	// Los productos de antes de HU-09, para BusquedaAlArrancar
	List<Producto> findByBusquedaIsNull();

	// Lo que el catálogo puede listar (HU-09 RF-2): activos de categorías visibles. Se repite en la consulta y en su
	// conteo
	String CATALOGO = """
			from Producto p
			where p.activo = true and p.categoria.visible = true
			  and (:texto is null or p.busqueda like :texto escape '!')
			  and (:idCategoria is null or p.categoria.idCategoria = :idCategoria)
			  and (:marca is null or lower(p.marca) = :marca)
			  and (:precioMin is null or p.precio >= :precioMin)
			  and (:precioMax is null or p.precio <= :precioMax)
			  and (:soloDisponibles = false or p.stock > 0)
			""";

	// texto llega como en buscar, pero ya sin tildes (Producto.textoDeBusqueda), y la marca en minúsculas. Los
	// agotados van al final; después, el orden que pide la página (OrdenCatalogo)
	@Query(value = "select p " + CATALOGO + " order by case when p.stock > 0 then 0 else 1 end",
			countQuery = "select count(p) " + CATALOGO)
	Page<Producto> catalogo(@Param("texto") String texto, @Param("idCategoria") Long idCategoria,
			@Param("marca") String marca, @Param("precioMin") BigDecimal precioMin,
			@Param("precioMax") BigDecimal precioMax, @Param("soloDisponibles") boolean soloDisponibles,
			Pageable paginacion);

	// Los carruseles de la portada (HU-09 RF-3, RF-7): solo disponibles; por categoría y solo destacados si se pide
	@Query("""
			select p from Producto p
			where p.activo = true and p.categoria.visible = true and p.stock > 0
			  and (:idCategoria is null or p.categoria.idCategoria = :idCategoria)
			  and (:soloDestacados = false or p.destacado = true)
			""")
	List<Producto> vitrina(@Param("idCategoria") Long idCategoria, @Param("soloDestacados") boolean soloDestacados,
			Pageable paginacion);

	// El detalle público (HU-10 RF-8): inactivo, de una categoría oculta o inexistente da lo mismo; agotado sí sale
	@EntityGraph(attributePaths = "categoria")
	@Query("select p from Producto p where p.idProducto = :id and p.activo = true and p.categoria.visible = true")
	Optional<Producto> publico(@Param("id") Long idProducto);

	// El filtro por marca (HU-09 RF-9): las de lo que se puede listar, también agotado
	@Query("""
			select distinct p.marca from Producto p
			where p.activo = true and p.categoria.visible = true and p.marca is not null
			order by p.marca
			""")
	List<String> marcasDelCatalogo();

	boolean existsByCategoria_IdCategoria(Long idCategoria);

	long countByCategoria_IdCategoria(Long idCategoria);

	// Cuántos productos tiene cada categoría, activos o no; las que no tienen ninguno no aparecen
	@Query("select p.categoria.idCategoria as idCategoria, count(p) as productos from Producto p group by p.categoria.idCategoria")
	List<ConteoPorCategoria> contarPorCategoria();

	// Serializa los cambios de imágenes del mismo producto: el quinto lugar no se ocupa dos veces, y ningún archivo
	// queda sin una fila que lo nombre
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Producto> findConBloqueoByIdProducto(Long idProducto);

	record ConteoPorCategoria(Long idCategoria, Long productos) {
	}
}
