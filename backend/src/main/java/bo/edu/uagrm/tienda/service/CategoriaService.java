package bo.edu.uagrm.tienda.service;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import bo.edu.uagrm.tienda.dto.CategoriaRequest;
import bo.edu.uagrm.tienda.entity.Categoria;
import bo.edu.uagrm.tienda.exception.CategoriaExistenteException;
import bo.edu.uagrm.tienda.exception.CategoriaNoEncontradaException;
import bo.edu.uagrm.tienda.exception.ImagenInvalidaException;
import bo.edu.uagrm.tienda.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;

// Categorías del catálogo (HU-06; decisiones.md → Categorías del catálogo)
@Service
@RequiredArgsConstructor
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;
	private final AlmacenImagenes almacen;

	@Transactional(readOnly = true)
	public List<Categoria> visibles() {
		return categoriaRepository.findByVisibleTrueOrderByNombreClave();
	}

	@Transactional(readOnly = true)
	public List<Categoria> todas() {
		return categoriaRepository.findAllByOrderByNombreClave();
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
		Categoria categoria = buscar(idCategoria);
		if (categoriaRepository.existsByNombreClaveAndIdCategoriaNot(Categoria.clave(solicitud.nombre()),
				idCategoria)) {
			throw new CategoriaExistenteException();
		}
		categoria.editar(solicitud.nombre(), solicitud.descripcion());
		return guardar(categoria);
	}

	@Transactional
	public Categoria cambiarVisibilidad(Long idCategoria, boolean visible) {
		Categoria categoria = buscar(idCategoria);
		if (visible) {
			categoria.mostrar();
		} else {
			categoria.ocultar();
		}
		return categoria;
	}

	// RF-10 (no borrar una categoría con productos) se agrega en HU-07, cuando existan los productos
	@Transactional
	public void borrar(Long idCategoria) {
		Categoria categoria = bloquear(idCategoria);
		categoriaRepository.delete(categoria);
		alTerminar(categoria.getImagen(), null);
	}

	// El formato sale del contenido, no del nombre ni del tipo que declara el navegador (decisiones.md → Imágenes
	// subidas). El archivo nuevo se guarda antes de tocar la fila; después del commit se borra el anterior y, si la
	// transacción se revierte, el nuevo: un fallo nunca deja la categoría apuntando a un archivo borrado
	@Transactional
	public Categoria cambiarImagen(Long idCategoria, byte[] contenido) {
		FormatoImagen formato = FormatoImagen.detectar(contenido).orElseThrow(ImagenInvalidaException::new);
		Categoria categoria = bloquear(idCategoria);
		String anterior = categoria.getImagen();
		String nueva = almacen.guardar(contenido, formato);
		alTerminar(anterior, nueva);
		categoria.cambiarImagen(nueva);
		return categoria;
	}

	@Transactional
	public Categoria quitarImagen(Long idCategoria) {
		Categoria categoria = bloquear(idCategoria);
		alTerminar(categoria.getImagen(), null);
		categoria.cambiarImagen(null);
		return categoria;
	}

	@Transactional(readOnly = true)
	public Optional<ImagenGuardada> imagen(Long idCategoria) {
		return Optional.ofNullable(buscar(idCategoria).getImagen()).flatMap(nombre -> almacen.leer(nombre)
				.map(contenido -> new ImagenGuardada(contenido, FormatoImagen.deArchivo(nombre))));
	}

	public record ImagenGuardada(byte[] contenido, FormatoImagen formato) {
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

	// Al confirmar se borra el primer archivo; al revertir, el segundo. Sin transacción activa (pruebas
	// unitarias) se aplica enseguida, como si se confirmara
	private void alTerminar(String siSeConfirma, String siSeRevierte) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			borrarSiHay(siSeConfirma);
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int estado) {
				borrarSiHay(estado == STATUS_COMMITTED ? siSeConfirma : siSeRevierte);
			}
		});
	}

	private void borrarSiHay(String imagen) {
		if (imagen != null) {
			almacen.borrar(imagen);
		}
	}
}
