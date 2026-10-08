package bo.edu.uagrm.tienda.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import bo.edu.uagrm.tienda.exception.ErrorNegocioException;

// Se cuenta por correo e IP para que nadie bloquee a otro desde su propia dirección. Los contadores viven en memoria:
// se pierden al reiniciar y suponen una sola instancia del backend
public abstract class LimiteDeFrecuencia {

	private static final Duration INTERVALO_LIMPIEZA = Duration.ofMinutes(1);

	private final Clock clock;
	private final int intentosMaximos;
	private final Duration ventana;
	private final Duration bloqueo;
	private final Map<Clave, Registro> registrosPorClave = new ConcurrentHashMap<>();
	private final AtomicReference<Instant> ultimaLimpieza = new AtomicReference<>(Instant.MIN);

	protected LimiteDeFrecuencia(Clock clock, int intentosMaximos, Duration ventana, Duration bloqueo) {
		this.clock = clock;
		this.intentosMaximos = intentosMaximos;
		this.ventana = ventana;
		this.bloqueo = bloqueo;
	}

	protected abstract ErrorNegocioException limiteExcedido();

	// Verifica el bloqueo y cuenta el intento en una sola operación atómica: así una ráfaga de peticiones
	// simultáneas no puede superar el máximo
	public void registrarIntento(String correo, String ip) {
		Instant ahora = clock.instant();
		limpiarSinEfecto(ahora);
		registrosPorClave.compute(clave(correo, ip), (clave, previo) -> {
			Registro vigente = previo == null ? Registro.VACIO : vigenteEn(previo, ahora);
			if (vigente.bloqueadoEn(ahora)) {
				throw limiteExcedido();
			}
			return conIntento(vigente, ahora);
		});
	}

	public void anularIntento(String correo, String ip) {
		registrosPorClave.computeIfPresent(clave(correo, ip), (clave, registro) -> sinUltimoIntento(registro));
	}

	public void registrarExito(String correo, String ip) {
		registrosPorClave.remove(clave(correo, ip));
	}

	public void reiniciarCorreo(String correo) {
		registrosPorClave.keySet().removeIf(clave -> clave.correo().equals(correo));
	}

	// Recorrer el mapa en cada petición haría que muchos correos distintos degraden todas las peticiones: se limpia
	// como mucho una vez por minuto
	private void limpiarSinEfecto(Instant ahora) {
		Instant anterior = ultimaLimpieza.get();
		if (ahora.isBefore(anterior.plus(INTERVALO_LIMPIEZA)) || !ultimaLimpieza.compareAndSet(anterior, ahora)) {
			return;
		}
		registrosPorClave.values().removeIf(registro -> sinEfecto(registro, ahora));
	}

	int registrosEnMemoria() {
		return registrosPorClave.size();
	}

	private static Clave clave(String correo, String ip) {
		return new Clave(correo, ip);
	}

	// Ventana móvil: solo cuentan los intentos de la ventana, y el bloqueo cumplido se descarta
	private Registro vigenteEn(Registro registro, Instant ahora) {
		Instant inicioVentana = ahora.minus(ventana);
		return new Registro(registro.intentos().stream().filter(intento -> intento.isAfter(inicioVentana)).toList(),
				registro.bloqueadoEn(ahora) ? registro.bloqueadoHasta() : null);
	}

	private Registro conIntento(Registro registro, Instant ahora) {
		List<Instant> todos = Stream.concat(registro.intentos().stream(), Stream.of(ahora)).toList();
		return new Registro(todos, todos.size() >= intentosMaximos ? ahora.plus(bloqueo) : registro.bloqueadoHasta());
	}

	private Registro sinUltimoIntento(Registro registro) {
		List<Instant> intentos = registro.intentos();
		if (intentos.isEmpty()) {
			return registro;
		}
		List<Instant> restantes = List.copyOf(intentos.subList(0, intentos.size() - 1));
		return new Registro(restantes, restantes.size() >= intentosMaximos ? registro.bloqueadoHasta() : null);
	}

	private boolean sinEfecto(Registro registro, Instant ahora) {
		Registro vigente = vigenteEn(registro, ahora);
		return vigente.intentos().isEmpty() && vigente.bloqueadoHasta() == null;
	}

	private record Clave(String correo, String ip) {
	}

	private record Registro(List<Instant> intentos, Instant bloqueadoHasta) {

		static final Registro VACIO = new Registro(List.of(), null);

		boolean bloqueadoEn(Instant ahora) {
			return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
		}
	}
}
