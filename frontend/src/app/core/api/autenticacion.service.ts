import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ErrorApi, esErrorApi } from '../modelos/error-api';
import { DatosDeRegistro } from '../modelos/registro';
import { Sesion } from '../modelos/sesion';

// Llamadas a /api/auth. El proxy de desarrollo las reenvía al backend, así que
// nunca se escribe una dirección absoluta.
@Injectable({ providedIn: 'root' })
export class AutenticacionService {
	private readonly http = inject(HttpClient);

	// La contraseña viaja tal cual: distingue mayúsculas y los espacios cuentan.
	// El correo se normaliza igual que en el registro.
	async iniciarSesion(correo: string, contrasena: string): Promise<Sesion> {
		try {
			return await firstValueFrom(
				this.http.post<Sesion>('/api/auth/inicio-sesion', {
					correo: correo.trim().toLowerCase(),
					contrasena,
				}),
			);
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// El backend vuelve a validar y a normalizar todo esto: acá se manda limpio
	// para que un espacio de más no termine guardado en la cuenta.
	// El teléfono es opcional y, si queda vacío, viaja como null.
	// Responde con la sesión de la cuenta nueva, como el inicio de sesión.
	async registrarCliente(datos: DatosDeRegistro): Promise<Sesion> {
		const telefono = datos.telefono.trim();
		try {
			return await firstValueFrom(
				this.http.post<Sesion>('/api/auth/registro', {
					nombre: datos.nombre.trim(),
					apellido: datos.apellido.trim(),
					correo: datos.correo.trim().toLowerCase(),
					contrasena: datos.contrasena,
					telefono: telefono === '' ? null : telefono,
				}),
			);
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// La respuesta trae un mensaje de confirmación que no se usa: el
	// frontend muestra siempre el suyo, igual exista o no la cuenta (RF-3).
	async solicitarRecuperacion(correo: string): Promise<void> {
		try {
			await firstValueFrom(
				this.http.post('/api/auth/recuperacion', { correo: correo.trim().toLowerCase() }),
			);
		} catch (error) {
			throw comoErrorApi(error);
		}
	}

	// El token viaja tal como llegó en el enlace, solo sin espacios de sobra: el
	// backend lo hashea y compara, cualquier otro cambio lo invalidaría.
	async restablecerContrasena(token: string, contrasena: string): Promise<void> {
		try {
			await firstValueFrom(
				this.http.post('/api/auth/recuperacion/restablecimiento', {
					token: token.trim(),
					contrasena,
				}),
			);
		} catch (error) {
			throw comoErrorApi(error);
		}
	}
}

// El cuerpo es un ProblemDetail. Si no llegó respuesta (red caída, servidor
// apagado), se arma un error propio con el mismo formato.
export function comoErrorApi(error: unknown): ErrorApi {
	if (error instanceof HttpErrorResponse && esErrorApi(error.error)) {
		return error.error;
	}
	return { status: 0, detail: 'No se pudo contactar al servidor', codigo: 'SIN_CONEXION' };
}
