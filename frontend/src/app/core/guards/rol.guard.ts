import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Rol } from '../modelos/usuario';
import { SesionService } from '../sesion/sesion.service';

// La ruta declara el rol que necesita: { data: { rol: 'ADMINISTRADOR' } }.
// Igual que sesionGuard, esto es usabilidad, no control de acceso.
export const rolGuard: CanActivateFn = (ruta, estado) => {
	const sesion = inject(SesionService);
	const router = inject(Router);

	if (!sesion.autenticado()) {
		return router.createUrlTree(['/inicio-sesion'], {
			queryParams: { destino: estado.url },
		});
	}

	// Con sesión pero sin el rol, vuelve al inicio: no es una sesión caída
	const exigido = ruta.data['rol'] as Rol | undefined;
	return exigido === undefined || sesion.rol() === exigido ? true : router.createUrlTree(['/']);
};
