package bo.edu.uagrm.tienda.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.extern.slf4j.Slf4j;

// Lo que no se puede deshacer, como enviar un correo o borrar un archivo, espera a que termine la transacción: si
// fallara después, el correo llevaría un enlace que no quedó guardado. Sin transacción (pruebas unitarias), se hace en
// el momento, como si se confirmara. Un fallo de la tarea solo va al log: los datos ya se confirmaron, y la respuesta
// no puede decir que no
@Slf4j
final class TrasElCommit {

	private TrasElCommit() {
	}

	static void ejecutar(Runnable tarea) {
		alTerminar(tarea, () -> {
		});
	}

	static void alTerminar(Runnable siSeConfirma, Runnable siSeRevierte) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			siSeConfirma.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int estado) {
				try {
					(estado == STATUS_COMMITTED ? siSeConfirma : siSeRevierte).run();
				} catch (RuntimeException error) {
					log.error("Falló una tarea de después de la transacción", error);
				}
			}
		});
	}
}
