import { computed, DestroyRef, inject, Injectable, NgZone, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Sesion } from '../modelos/sesion';
import { Rol, Usuario } from '../modelos/usuario';
import { parametrosDelDestino } from '../../shared/navegacion/destino-interno';
import { borrarSesion, guardarSesion, leerSesion } from './almacen-token';

// Estado de la sesión para toda la aplicación. Quien autoriza de verdad es el
// backend: acá solo se guarda lo necesario para mostrar la interfaz que
// corresponde y para enviar el token en cada petición.
@Injectable({ providedIn: 'root' })
export class SesionService {
	private readonly sesion = signal<Sesion | null>(leerSesion());
	private temporizador: ReturnType<typeof setTimeout> | null = null;
	private readonly zona = inject(NgZone);
	private readonly router = inject(Router);

	readonly usuario = computed<Usuario | null>(() => this.sesion()?.usuario ?? null);
	readonly rol = computed<Rol | null>(() => this.sesion()?.usuario.rol ?? null);
	readonly token = computed<string | null>(() => this.sesion()?.token ?? null);
	readonly autenticado = computed<boolean>(() => this.sesion() !== null);

	constructor() {
		this.programarCierre();
		inject(DestroyRef).onDestroy(() => this.cancelarCierre());
	}

	iniciar(sesion: Sesion): void {
		guardarSesion(sesion);
		this.sesion.set(sesion);
		this.programarCierre();
	}

	cerrar(): void {
		this.cancelarCierre();
		borrarSesion();
		this.sesion.set(null);
	}

	// La sesión se cerró sola (venció el token o el servidor lo rechazó): se
	// avisa en «Iniciar sesión» y, al volver a entrar, se vuelve a donde estaba
	// (HU-02 RF-10). «Cerrar sesión» a pedido usa cerrar(), sin aviso.
	// Si ya no hay sesión no hace nada: varias peticiones que vuelven con 401 a la vez
	// no deben navegar dos veces ni perder el destino.
	vencer(): void {
		if (!this.autenticado()) {
			return;
		}
		this.cerrar();
		const actual = this.router.url;
		const destino = parametrosDelDestino(actual.startsWith('/inicio-sesion') ? null : actual);
		void this.router.navigate(['/inicio-sesion'], { queryParams: { aviso: 'sesion-cerrada', ...destino } });
	}

	// los datos de la cuenta cambiaron pero el token sigue valiendo. Se
	// guarda de nuevo para que una recarga no vuelva a mostrar los anteriores.
	actualizarUsuario(usuario: Usuario): void {
		const actual = this.sesion();
		if (actual === null) {
			return;
		}
		const nueva: Sesion = { ...actual, usuario };
		guardarSesion(nueva);
		this.sesion.set(nueva);
	}

	esAdministrador(): boolean {
		return this.rol() === 'ADMINISTRADOR';
	}

	// Sin esto, una pestaña abierta seguiría mostrándose como autenticada después
	// de que el token venciera, hasta que alguna petición volviera con 401
	private programarCierre(): void {
		this.cancelarCierre();
		const sesion = this.sesion();
		if (sesion === null) {
			return;
		}

		// Fuera de la zona de Angular: si no, esta espera de horas mantendría a la
		// aplicación como "ocupada" y las pruebas nunca se estabilizarían
		const restante = new Date(sesion.expiracion).getTime() - Date.now();
		this.temporizador = this.zona.runOutsideAngular(() =>
			setTimeout(() => this.zona.run(() => this.vencer()), Math.max(restante, 0)),
		);
	}

	private cancelarCierre(): void {
		if (this.temporizador !== null) {
			clearTimeout(this.temporizador);
			this.temporizador = null;
		}
	}
}
