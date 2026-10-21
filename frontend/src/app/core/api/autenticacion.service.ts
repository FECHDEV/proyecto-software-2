import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

import { DatosDeRegistro } from '../modelos/registro';
import { Sesion } from '../modelos/sesion';
import { escribir } from './escribir';

// Llamadas a /api/auth. El proxy de desarrollo las reenvía al backend, así que
// nunca se escribe una dirección absoluta.
@Injectable({ providedIn: 'root' })
export class AutenticacionService {
	private readonly http = inject(HttpClient);

	// La contraseña viaja tal cual: distingue mayúsculas y los espacios cuentan.
	// El correo se normaliza igual que en el registro.
	async iniciarSesion(correo: string, contrasena: string): Promise<Sesion> {
		return await escribir(
			this.http.post<Sesion>('/api/auth/inicio-sesion', {
				correo: correo.trim().toLowerCase(),
				contrasena,
			}),
		);
	}

	// El backend vuelve a validar y a normalizar todo esto: acá se manda limpio
	// para que un espacio de más no termine guardado en la cuenta.
	// El teléfono es opcional y, si queda vacío, viaja como null.
	// Responde con la sesión de la cuenta nueva, como el inicio de sesión.
	async registrarCliente(datos: DatosDeRegistro): Promise<Sesion> {
		const telefono = datos.telefono.trim();
		return await escribir(
			this.http.post<Sesion>('/api/auth/registro', {
				nombre: datos.nombre.trim(),
				apellido: datos.apellido.trim(),
				correo: datos.correo.trim().toLowerCase(),
				contrasena: datos.contrasena,
				telefono: telefono === '' ? null : telefono,
			}),
		);
	}

	// La respuesta trae un mensaje de confirmación que no se usa: el
	// frontend muestra siempre el suyo, igual exista o no la cuenta (RF-3).
	async solicitarRecuperacion(correo: string): Promise<void> {
		await escribir(
			this.http.post('/api/auth/recuperacion', { correo: correo.trim().toLowerCase() }),
		);
	}

	// El token viaja tal como llegó en el enlace, solo sin espacios de sobra: el
	// backend lo hashea y compara, cualquier otro cambio lo invalidaría.
	async restablecerContrasena(token: string, contrasena: string): Promise<void> {
		await escribir(
			this.http.post('/api/auth/recuperacion/restablecimiento', {
				token: token.trim(),
				contrasena,
			}),
		);
	}

	// Como el de la recuperación: el token viaja como llegó, sin espacios de sobra (HU-20 RF-4)
	async verificarCorreo(token: string): Promise<void> {
		await escribir(this.http.post('/api/auth/verificacion', { token: token.trim() }));
	}
}
