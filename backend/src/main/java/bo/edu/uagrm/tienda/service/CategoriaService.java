package bo.edu.uagrm.tienda.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bo.edu.uagrm.tienda.dto.CategoriaRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.exception.CategoriaConProductosException;
import bo.edu.uagrm.tienda.exception.CategoriaExistenteException;
import bo.edu.uagrm.tienda.exception.CategoriaNoEncontradaException;
import bo.edu.uagrm.tienda.exception.ImagenDemasiadoGrandeException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import bo.edu.uagrm.tienda.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;

// Categorías del catálogo (HU-06; decisiones.md → Categorías del catálogo)
@Service
@RequiredArgsConstructor
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;
	private final ProductoRepository productoRepository;
	private final AlmacenImagenes almacen;

	@Transactional(readOnly = true)
	public List<Categoria> visibles() {
		return categoriaRepository.findByVisibleTrueOrderByNombreClave();
	}

	@Transactional(readOnly = true)
	public List<Categoria> todas() {
		return categoriaRepository.findAllByOrderByNombreClave();
	}

	// Cuántos productos tiene cada categoría, activos o no (HU-07 RF-13); las que no tienen ninguno no figuran
	@Transactional(readOnly = true)
	public Map<Long, Long> productosPorCategoria() {
		return productoRepository.contarPorCategoria().stream().collect(Collectors.toMap(
				ProductoRepository.ConteoPorCategoria::idCategoria, ProductoRepository.ConteoPorCategoria::productos));
	}

	@Transactional(readOnly = true)
	public long productos(Long idCategoria) {
		return productoRepository.countByCategoria_IdCategoria(idCategoria);
	}

	@Transactional
	public Categoria crear(CategoriaRequest solicitud) {
		if (categoriaRepository.existsByNombreClave(Categoria.clave(solicitud.nombre()))) {
			throw new CategoriaExistenteException();
		}
		return guardar(Categoria.crear(solicitud.nombre(), solicitud.descripcion()));
	}

	@Transactional
	public Categoria editar(Long idCategoria, CategoriaRequest solicitud) {
		Categoria categoria = bloquear(idCategoria);
		if (categoriaRepository.existsByNombreClaveAndIdCategoriaNot(Categoria.clave(solicitud.nombre()),
				idCategoria)) {
			throw new CategoriaExistenteException();
		}
		categoria.editar(solicitud.nombre(), solicitud.descripcion());
		return guardar(categoria);
	}

	@Transactional
	public Categoria cambiarVisibilidad(Long idCategoria, boolean visible) {
		Categoria categoria = bloquear(idCategoria);
		if (visible) {
			categoria.mostrar();
		} else {
			categoria.ocultar();
		}
		return categoria;
	}

	// Con productos, activos o no, no se borra: se oculta (HU-06 RF-10, HU-07 RF-12). Dar de alta o mover un
	// producto bloquea la categoría, así que ninguno llega mientras se borra
	@Transactional
	public void borrar(Long idCategoria) {
		Categoria categoria = bloquear(idCategoria);
		if (productoRepository.existsByCategoria_IdCategoria(idCategoria)) {
			throw new CategoriaConProductosException();
		}
		categoriaRepository.delete(categoria);
		ArchivosAlTerminar.borrar(almacen, categoria.getImagen(), null);
	}

	// El formato sale del contenido, no del nombre ni del tipo que declara el navegador (decisiones.md → Imágenes
	// subidas). El archivo nuevo se guarda antes de tocar la fila; después del commit se borra el anterior y, si la
	// transacción se revierte, el nuevo: un fallo nunca deja la categoría apuntando a un archivo borrado
	@Transactional
	public Categoria cambiarImagen(Long idCategoria, byte[] contenido) {
		if (contenido.length > FormatoImagen.MAXIMO) {
			throw new ImagenDemasiadoGrandeException();
		}
		FormatoImagen formato = FormatoImagen.detectar(contenido).orElseThrow(ImagenInvalidaException::new);
		Categoria categoria = bloquear(idCategoria);
		String anterior = categoria.getImagen();
		String nueva = almacen.guardar(contenido, formato);
		ArchivosAlTerminar.borrar(almacen, anterior, nueva);
		categoria.cambiarImagen(nueva);
		return categoria;
	}

	@Transactional
	public Categoria quitarImagen(Long idCategoria) {
		Categoria categoria = bloquear(idCategoria);
		ArchivosAlTerminar.borrar(almacen, categoria.getImagen(), null);
		categoria.cambiarImagen(null);
		return categoria;
	}

	@Transactional(readOnly = true)
	public Optional<ImagenGuardada> imagen(Long idCategoria) {
		return Optional.ofNullable(buscar(idCategoria).getImagen()).flatMap(nombre -> almacen.leer(nombre)
				.map(contenido -> new ImagenGuardada(contenido, FormatoImagen.deArchivo(nombre))));
	}

	private Categoria buscar(Long idCategoria) {
		return categoriaRepository.findById(idCategoria).orElseThrow(CategoriaNoEncontradaException::new);
	}

	private Categoria bloquear(Long idCategoria) {
		return categoriaRepository.findConBloqueoByIdCategoria(idCategoria)
				.orElseThrow(CategoriaNoEncontradaException::new);
	}

	private Categoria guardar(Categoria categoria) {
		try {
			return categoriaRepository.saveAndFlush(categoria);
		} catch (DataIntegrityViolationException e) {
			if (CategoriaRepository.esNombreDuplicado(e)) {
				throw new CategoriaExistenteException();
			}
			throw e;
		}
	}
}
