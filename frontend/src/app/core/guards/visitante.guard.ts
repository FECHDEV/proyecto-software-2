import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SesionService } from '../sesion/sesion.service';

// Pantallas para quien todavía no tiene sesión, como «Crear cuenta» (HU-01
// RF-14): con la sesión iniciada no tienen sentido y se va al inicio.
export const visitanteGuard: CanActivateFn = () => {
	const sesion = inject(SesionService);
	return sesion.autenticado() ? inject(Router).createUrlTree(['/']) : true;
};
