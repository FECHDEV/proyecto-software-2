import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';

import { SesionService } from '../sesion/sesion.service';

// Una pantalla con formularios que se pueden perder al irse
export interface ConCambiosSinGuardar {
	tieneCambiosSinGuardar(): boolean;
}

// Pregunta antes de salir de una pantalla con datos escritos y sin guardar.
// Sin sesión no pregunta: se sale porque la persona cerró sesión o porque la
// sesión cayó, y no queda nada que se pueda guardar.
export const cambiosSinGuardarGuard: CanDeactivateFn<ConCambiosSinGuardar> = (pantalla) => {
	if (!inject(SesionService).autenticado() || !pantalla.tieneCambiosSinGuardar()) {
		return true;
	}
	return window.confirm('Tienes cambios sin guardar. ¿Salir de todos modos?');
};
