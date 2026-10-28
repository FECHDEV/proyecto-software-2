package bo.edu.uagrm.tienda.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import bo.edu.uagrm.tienda.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// HU-22 RF-10: las fotos subidas antes de la historia se achican al arrancar (lo lanza VersionesAlArrancar). Sin
// bloquear la fila: el Administrador puede seguir trabajando mientras tanto
@Slf4j
@Component
@RequiredArgsConstructor
public class VersionesFaltantes {

	private final ProductoRepository productoRepository;
	private final AlmacenImagenes almacen;
	private final Achicador achicador;
	private final TransactionTemplate transaccion;

	public void completar() {
		for (Long idProducto : productoRepository.idsConImagenes()) {
			try {
				completar(idProducto);
			} catch (RuntimeException error) {
				// Un fallo al leer el producto no corta el recorrido (/code-review)
				log.warn("Las fotos del producto {} quedan sin revisar", idProducto, error);
			}
		}
	}

	private void completar(Long idProducto) {
		for (String foto : fotosDe(idProducto)) {
			if (almacen.tieneVersiones(foto)) {
				continue;
			}
			try {
				Optional<byte[]> original = almacen.leer(foto);
				if (original.isEmpty()) {
					continue;
				}
				Achicador.Versiones versiones = achicador.achicar(original.get());
				try {
					almacen.guardarVersion(foto, Achicador.ANCHOS[0], versiones.de400());
					almacen.guardarVersion(foto, Achicador.ANCHOS[1], versiones.de1200());
				} finally {
					// Si se quitó mientras se achicaba, quitarImagen ya borró lo que había y estas quedarían huérfanas.
					// Si se quita después de esta lectura, quitarImagen las borra al confirmar. También si falló la
					// segunda: la primera no queda suelta (/code-review)
					if (!fotosDe(idProducto).contains(foto)) {
						AlmacenImagenes.nombresDeVersiones(foto).forEach(almacen::borrar);
					}
				}
			} catch (RuntimeException error) {
				// La prevista (dañada, enorme, el disco) o cualquier otra: esa foto queda con el original y se sigue
				log.warn("La foto {} del producto {} queda sin versiones", foto, idProducto, error);
			}
		}
	}

	// En su propia transacción de lectura: la lista es perezosa
	private List<String> fotosDe(Long idProducto) {
		return transaccion.execute(estado -> productoRepository.findById(idProducto)
				.map(producto -> List.copyOf(producto.getImagenes())).orElse(List.of()));
	}
}
