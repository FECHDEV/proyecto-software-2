import { Sesion } from '../modelos/sesion';

// El token se guarda en localStorage para que la sesión sobreviva a recargar la
// página: sin refresh token, cualquier otra opción obligaría a iniciar sesión en
// cada recarga (docs/decisiones.md → Decisiones técnicas del frontend).
// Junto con core/tema/almacen-tema.ts, es lo único que toca el almacenamiento
// del navegador.
export const CLAVE_SESION = 'app.sesion';

export function guardarSesion(sesion: Sesion): void {
	try {
		localStorage.setItem(CLAVE_SESION, JSON.stringify(sesion));
	} catch {
		// Modo privado o almacenamiento lleno: la sesión vive solo en memoria
	}
}

export function leerSesion(ahora: Date = new Date()): Sesion | null {
	const guardado = leerCrudo();
	if (guardado === null) {
		return null;
	}

	const sesion = interpretar(guardado);
	if (sesion === null) {
		borrarSesion();
		return null;
	}

	if (new Date(sesion.expiracion).getTime() <= ahora.getTime()) {
		borrarSesion();
		return null;
	}

	return sesion;
}

export function borrarSesion(): void {
	try {
		localStorage.removeItem(CLAVE_SESION);
	} catch {
		// Ídem: no hay nada que borrar si tampoco se pudo escribir
	}
}

function leerCrudo(): string | null {
	try {
		return localStorage.getItem(CLAVE_SESION);
	} catch {
		return null;
	}
}

function interpretar(guardado: string): Sesion | null {
	let valor: unknown;
	try {
		valor = JSON.parse(guardado);
	} catch {
		return null;
	}

	if (valor === null || typeof valor !== 'object') {
		return null;
	}

	const posible = valor as Partial<Sesion>;
	const completa =
		typeof posible.token === 'string' &&
		typeof posible.expiracion === 'string' &&
		!Number.isNaN(new Date(posible.expiracion).getTime()) &&
		posible.usuario !== undefined &&
		posible.usuario !== null;

	return completa ? (posible as Sesion) : null;
}
