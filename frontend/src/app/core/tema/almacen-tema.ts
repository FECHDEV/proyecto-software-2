// El modo de la interfaz que eligió la persona, guardado en el navegador
// (docs/decisiones.md → Sistema de diseño (frontend)). Sin nada guardado, o si
// el almacenamiento no está disponible, el modo es el oscuro.
export type Tema = 'oscuro' | 'claro';

// index.html la lee antes de que arranque Angular, para no mostrar un destello
// del modo oscuro: si cambia acá, cambia allá
export const CLAVE_TEMA = 'app.tema';

export function leerTema(): Tema {
	try {
		return localStorage.getItem(CLAVE_TEMA) === 'claro' ? 'claro' : 'oscuro';
	} catch {
		return 'oscuro';
	}
}

export function guardarTema(tema: Tema): void {
	try {
		localStorage.setItem(CLAVE_TEMA, tema);
	} catch {
		// Modo privado o almacenamiento lleno: el tema vale hasta recargar
	}
}
