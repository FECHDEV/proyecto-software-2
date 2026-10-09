import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CambioDeContrasena, DatosPersonales } from '../modelos/cuenta';
import { Sesion } from '../modelos/sesion';
import { Usuario } from '../modelos/usuario';
import { comoErrorApi } from './autenticacion.service';

// Llamadas a /api/cuenta. La cuenta siempre es la de la sesión: el
// backend la saca del token, así que ninguna llamada lleva un id.
@Injectable({ providedIn: 'root' })
export class CuentaService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la
	// pantalla, y la petición vive mientras viva ella
	datosPersonales(): HttpResourceRef<Usuario | undefined> {
		return httpResource<Usuario>(() => '/api/cuenta/datos-personales');
	}

	// Limpio como en el registro; el teléfono vacío viaja como null (HU-04 RF-3)
	async actualizarDatos(datos: DatosPersonales): Promise<Usuario> {
		const telefono = datos.telefono.trim();
		try {
			return await firstValueFrom(
				this.http.put<Usuario>('/api/cuenta/datos-personales', {
					nombre: datos.nombre.trim(),
					apellido: datos.apellido.trim(),
					telefono: telefono === '' ? null : telefono,
				}),
			);
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// Las contraseñas viajan tal cual. La respuesta trae un token nuevo: el que
	// se usó para pedir el cambio deja de valer (RF-13).
	async cambiarContrasena(cambio: CambioDeContrasena): Promise<Sesion> {
		try {
			return await firstValueFrom(
				this.http.put<Sesion>('/api/cuenta/contrasena', {
					contrasenaActual: cambio.contrasenaActual,
					contrasenaNueva: cambio.contrasenaNueva,
				}),
			);
		} catch (error) {
			throw comoErrorApi(error);
		}
	}
}
