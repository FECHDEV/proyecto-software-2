import { linkedSignal, Resource, WritableSignal } from '@angular/core';

// Lo último que trajo un recurso: mientras carga otra página queda lo que se veía, para que la lista no parpadee
// y el foco no caiga al cuerpo de la página. Tras un error no queda nada: lo anterior es de otra búsqueda y
// volvería a verse al tocar «Reintentar» (revisión total, 27/10/2026)
export function ultimoValor<T>(recurso: Resource<T | undefined>): WritableSignal<T | undefined> {
	return linkedSignal<T | null | undefined, T | undefined>({
		source: () => (recurso.error() ? null : recurso.hasValue() ? recurso.value() : undefined),
		computation: (valor, previo) => (valor === null ? undefined : (valor ?? previo?.value)),
	});
}
