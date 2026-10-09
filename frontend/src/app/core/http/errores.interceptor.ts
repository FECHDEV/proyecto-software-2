import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { SesionService } from '../sesion/sesion.service';

const PUBLICO = '/api/auth/';

// Un 401 en una petición protegida significa que el token dejó de valer: venció,
// la cuenta se desactivó, cambió la contraseña o cambió el rol. En ese caso se cierra la sesión local, con
// el aviso de sesión cerrada (HU-02 RF-10).
// Un 403 es distinto: la sesión sigue siendo válida, solo que ese rol no entra.
export const erroresInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
	const sesion = inject(SesionService);

	return siguiente(peticion).pipe(
		catchError((error: unknown) => {
			const esSesionCaida =
				error instanceof HttpErrorResponse &&
				error.status === 401 &&
				!peticion.url.startsWith(PUBLICO);

			if (esSesionCaida) {
				sesion.vencer();
			}

			return throwError(() => error);
		}),
	);
};
