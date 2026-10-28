package bo.edu.uagrm.tienda.service;

import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import bo.edu.uagrm.tienda.exception.ErrorNegocioException;
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
					almacen.guardarVersion(foto, Achicador.ANCHOS[0], versiones.de400());
					almacen.guardarVersion(foto, Achicador.ANCHOS[1], versiones.de1200());
					// Si se quitó mientras se achicaba, quitarImagen ya borró lo que había y estas quedarían huérfanas.
					// Si se quita después de esta lectura, quitarImagen las borra al confirmar
					if (!fotosDe(idProducto).contains(foto)) {
						AlmacenImagenes.nombresDeVersiones(foto).forEach(almacen::borrar);
					}
				} catch (ErrorNegocioException | UncheckedIOException error) {
					log.warn("La foto {} del producto {} queda sin versiones: {}", foto, idProducto,
							error.getMessage());
				}
			}
		}
	}

	// En su propia transacción de lectura: la lista es perezosa
	private List<String> fotosDe(Long idProducto) {
		return transaccion.execute(estado -> productoRepository.findById(idProducto)
				.map(producto -> List.copyOf(producto.getImagenes())).orElse(List.of()));
	}
}
