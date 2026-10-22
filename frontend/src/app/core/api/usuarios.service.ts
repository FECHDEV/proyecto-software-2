import { HttpClient, httpResource, HttpResourceRef } from '@angular/common/http';
import { inject, Injectable, Signal } from '@angular/core';

import { Pagina } from '../modelos/pagina';
import { EstadoCuenta, Rol, TipoCliente } from '../modelos/usuario';
import { FiltrosDeUsuarios, UsuarioResumen } from '../modelos/usuario-resumen';
import { escribir } from './escribir';

// Llamadas a /api/usuarios. Solo el administrador llega: el backend lo
// valida en cada petición, la pantalla solo evita mostrarse a otros roles.
@Injectable({ providedIn: 'root' })
export class UsuariosService {
	private readonly http = inject(HttpClient);

	// httpResource necesita un contexto de inyección: se llama al construir la
	// pantalla. Cada cambio de filtros cancela la petición anterior y pide otra.
	listado(filtros: Signal<FiltrosDeUsuarios>): HttpResourceRef<Pagina<UsuarioResumen> | undefined> {
		return httpResource<Pagina<UsuarioResumen>>(() => ({
			url: '/api/usuarios',
			params: parametros(filtros()),
		}));
	}

	async cambiarRol(idUsuario: number, rol: Rol): Promise<UsuarioResumen> {
		return await escribir(this.http.put<UsuarioResumen>(`/api/usuarios/${idUsuario}/rol`, { rol }));
	}

	// HU-18 RF-21
	async cambiarTipo(idUsuario: number, tipo: TipoCliente): Promise<UsuarioResumen> {
		return await escribir(this.http.put<UsuarioResumen>(`/api/usuarios/${idUsuario}/tipo`, { tipo }));
	}

	async cambiarEstado(idUsuario: number, estado: EstadoCuenta): Promise<UsuarioResumen> {
		return await escribir(
			this.http.put<UsuarioResumen>(`/api/usuarios/${idUsuario}/estado`, { estado }),
		);
	}
}

// Solo viaja lo que filtra: sin rol, estado ni tipo son todos, un texto de solo
// espacios no filtra y la página 0 es la que el backend usa por defecto
function parametros(filtros: FiltrosDeUsuarios): Record<string, string> {
	const resultado: Record<string, string> = {};
	if (filtros.rol !== null) {
		resultado['rol'] = filtros.rol;
	}
	if (filtros.estado !== null) {
		resultado['estado'] = filtros.estado;
	}
	if (filtros.tipo !== null) {
		resultado['tipo'] = filtros.tipo;
	}
	const texto = filtros.texto.trim();
	if (texto !== '') {
		resultado['texto'] = texto;
	}
	if (filtros.pagina > 0) {
		resultado['pagina'] = String(filtros.pagina);
	}
	return resultado;
}
