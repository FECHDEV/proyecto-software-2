package bo.edu.uagrm.tienda.repository;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

// Un error de integridad que viene de una restricción única (un duplicado), y no de un dato demasiado largo u otra
// restricción. Lo usan los repositorios cuya tabla tiene una sola clave única
final class ViolacionesDeIntegridad {

	private ViolacionesDeIntegridad() {
	}

	static boolean esViolacionUnica(DataIntegrityViolationException ex) {
		for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacion) {
				return violacion.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
			}
		}
		return false;
	}
}
