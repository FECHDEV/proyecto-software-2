import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { SesionService } from '../sesion/sesion.service';

const API = '/api/';
const PUBLICO = '/api/auth/';

// Agrega el token a las peticiones de la API, salvo a las de autenticación:
// esas se atienden sin sesión, y un token vencido en el header solo estorba.
export const tokenInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
	const token = inject(SesionService).token();
	const esDeLaApi = peticion.url.startsWith(API);
	const esPublica = peticion.url.startsWith(PUBLICO);

	if (token === null || !esDeLaApi || esPublica) {
		return siguiente(peticion);
	}

	return siguiente(
		peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }),
	);
};
