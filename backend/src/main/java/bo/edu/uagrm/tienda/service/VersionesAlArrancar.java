package bo.edu.uagrm.tienda.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// HU-22 RF-10: achicar decenas de fotos lleva segundos, y la tienda no los espera: corre en un hilo aparte, con la
// aplicación ya lista (después del catálogo de demostración, que guarda las suyas con versiones). Mientras falte una
// versión se sirve el original. En las pruebas no corre: VersionesFaltantes se llama directo
@Component
@Profile("!test")
@RequiredArgsConstructor
public class VersionesAlArrancar {

	private final VersionesFaltantes versionesFaltantes;

	@EventListener(ApplicationReadyEvent.class)
	public void alArrancar() {
		Thread.ofPlatform().name("versiones-faltantes").daemon().start(versionesFaltantes::completar);
	}
}
