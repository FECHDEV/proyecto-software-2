import { HttpResourceRef } from '@angular/common/http';
import { computed, linkedSignal, Signal } from '@angular/core';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { comoErrorApi } from '../../core/modelos/error-api';

// Lo que muestra una sección de los reportes mientras su lectura cambia: queda
// lo último que se mostró hasta que llega lo nuevo, así no parpadea al cambiar
// de página o de período (como la lista de pedidos de HU-17)
export function ultimoValor<T>(recurso: HttpResourceRef<T | undefined>): Signal<T | undefined> {
	return linkedSignal<T | undefined, T | undefined>({
		source: () => (recurso.hasValue() ? recurso.value() : undefined),
		computation: (valor, previo) => valor ?? previo?.value,
	});
}

// El error de la lectura, con el diccionario; vacío si no hay (RF-22)
export function errorDeLectura(recurso: HttpResourceRef<unknown>): Signal<string> {
	return computed(() => {
		const error = recurso.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});
}
