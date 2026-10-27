import { inject } from '@angular/core';
import { CanDeactivateFn } from '@angular/router';

import { PedidoDeConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { SesionService } from '../sesion/sesion.service';

// Una pantalla con formularios que se pueden perder al irse. Pregunta con su
// diálogo (DialogoConfirmacion): window.confirm no seguía el diseño ni decía
// qué hace cada botón (revisión total)
export interface ConCambiosSinGuardar {
	tieneCambiosSinGuardar(): boolean;
	confirmarSalida(): Promise<boolean>;
}

export const SALIR_SIN_GUARDAR: PedidoDeConfirmacion = {
	titulo: 'Tiene cambios sin guardar',
	mensaje: 'Si sale ahora, lo que escribió se pierde.',
	confirmar: 'Salir sin guardar',
	desistir: 'Seguir editando',
};

// Pregunta antes de salir de una pantalla con datos escritos y sin guardar.
// Sin sesión no pregunta: se sale porque la persona cerró sesión o porque la
// sesión cayó, y no queda nada que se pueda guardar.
export const cambiosSinGuardarGuard: CanDeactivateFn<ConCambiosSinGuardar> = (pantalla) => {
	if (!inject(SesionService).autenticado() || !pantalla.tieneCambiosSinGuardar()) {
		return true;
	}
	return pantalla.confirmarSalida();
};
