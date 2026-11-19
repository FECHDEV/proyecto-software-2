package bo.edu.uagrm.tienda.service;

import java.util.concurrent.ConcurrentHashMap;

// Una consulta por cobro a la vez (decisiones.md → Pagos, 18/11/2026). La que llega mientras otra consulta no
// espera: le pide una vuelta más y vuelve. Así un pago que llegó durante la consulta en curso no se pierde, y
// cien avisos del mismo cobro son como mucho dos consultas seguidas, nunca cien a la vez (HU-13 RF-10, RF-11)
final class UnaConsultaPorCobro {

	// false: nadie pidió otra vuelta todavía; true: hay que consultar una vez más al terminar
	private final ConcurrentHashMap<Long, Boolean> enCurso = new ConcurrentHashMap<>();

	void ejecutar(Long idCobro, Runnable consulta) {
		while (enCurso.putIfAbsent(idCobro, Boolean.FALSE) != null) {
			if (enCurso.replace(idCobro, Boolean.TRUE) != null) {
				return; // la que consulta hará otra vuelta
			}
			// La otra terminó entre las dos líneas: se intenta ser la que consulta
		}
		try {
			do {
				consulta.run();
				// remove(clave, false) es atómico: sale solo si nadie pidió otra vuelta mientras consultaba
			} while (!enCurso.remove(idCobro, Boolean.FALSE) && enCurso.replace(idCobro, Boolean.FALSE) != null);
		} catch (RuntimeException | Error error) {
			// El cobro queda libre. Una vuelta pedida mientras fallaba se pierde: la tarea de cada minuto lo concilia
			// igual (/code-review)
			enCurso.remove(idCobro);
			throw error;
		}
	}
}
