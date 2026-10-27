import {
	ChangeDetectionStrategy,
	Component,
	computed,
	DestroyRef,
	inject,
	linkedSignal,
	signal,
	viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';

import { mensajeDeError } from '../../core/api/mensajes-error';
import { UsuariosService } from '../../core/api/usuarios.service';
import {
	EstadoCuenta,
	ESTADOS,
	NOMBRES_DE_ESTADO,
	NOMBRES_DE_ROL,
	NOMBRES_DE_TIPO,
	Rol,
	ROLES,
	TipoCliente,
	TIPOS_DE_CLIENTE,
} from '../../core/modelos/usuario';
import { comoError, comoErrorApi } from '../../core/modelos/error-api';
import { FiltrosDeUsuarios, UsuarioResumen } from '../../core/modelos/usuario-resumen';
import { SesionService } from '../../core/sesion/sesion.service';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { fechaCorta } from '../../shared/formato/fechas';
import { paginaDeLaUrl } from '../../shared/navegacion/pagina-de-la-url';
import { ultimoValor } from '../../shared/recursos/ultimo-valor';

// Cuánto se espera después de la última tecla antes de buscar
const ESPERA_DE_BUSQUEDA_MS = 300;

// Pantalla de la gestión de usuarios. Los filtros y la página viven en la URL: recargar o
// compartir el enlace muestra la misma lista.
@Component({
	selector: 'app-usuarios',
	imports: [DialogoConfirmacion],
	templateUrl: './usuarios.page.html',
	styleUrl: './usuarios.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsuariosPage {
	private readonly usuarios = inject(UsuariosService);
	private readonly ruta = inject(ActivatedRoute);
	private readonly router = inject(Router);
	private readonly sesion = inject(SesionService);
	private readonly dialogo = viewChild.required(DialogoConfirmacion);

	private readonly consulta = toSignal(this.ruta.queryParamMap, { requireSync: true });
	protected readonly filtros = computed(() => leerFiltros(this.consulta()));

	protected readonly listado = this.usuarios.listado(this.filtros);
	protected readonly pagina = ultimoValor(this.listado);
	// Un 403 (otro administrador le quitó el rol) o la red caída, con el diccionario
	protected readonly errorDeCarga = computed(() => {
		const error = this.listado.error();
		return error === undefined ? '' : mensajeDeError(comoErrorApi(error));
	});

	// Lo que está escrito en la búsqueda, que llega a la URL con un poco de espera.
	// Cuando la URL trae el texto que esta misma búsqueda mandó, lo escrito se
	// conserva: la URL lo guarda recortado, y mientras llega la persona pudo
	// seguir escribiendo. Solo otro texto (volver atrás, un enlace) lo reemplaza.
	private textoEnviado: string | null = null;
	protected readonly textoEscrito = linkedSignal<string, string>({
		source: () => this.filtros().texto,
		computation: (texto, previo) =>
			previo !== undefined && texto === this.textoEnviado ? previo.value : texto,
	});
	private espera: ReturnType<typeof setTimeout> | null = null;

	// La fila propia no ofrece acciones: el backend las rechazaría (RF-13, RF-14)
	protected readonly idPropio = computed(() => this.sesion.usuario()?.idUsuario ?? null);
	// Las cuentas que se están guardando, para no mandar dos veces la misma
	// operación. Es un conjunto: que termine una no habilita otra en curso.
	protected readonly procesando = signal<ReadonlySet<number>>(new Set());
	protected readonly avisoDeError = signal('');
	protected readonly avisoDeExito = signal('');

	protected readonly roles = ROLES;
	protected readonly estados = ESTADOS;
	protected readonly nombresDeRol = NOMBRES_DE_ROL;
	protected readonly nombresDeEstado = NOMBRES_DE_ESTADO;
	protected readonly tipos = TIPOS_DE_CLIENTE;
	protected readonly nombresDeTipo = NOMBRES_DE_TIPO;
	protected readonly fecha = fechaCorta;

	constructor() {
		inject(DestroyRef).onDestroy(() => this.cancelarEspera());
	}

	protected alEscribir(texto: string): void {
		this.textoEscrito.set(texto);
		this.cancelarEspera();
		this.espera = setTimeout(() => {
			this.textoEnviado = texto.trim();
			this.filtrar({ texto });
		}, ESPERA_DE_BUSQUEDA_MS);
	}

	protected alElegirRol(valor: string): void {
		this.filtrar({ rol: esRol(valor) ? valor : null });
	}

	protected alElegirEstado(valor: string): void {
		this.filtrar({ estado: esEstado(valor) ? valor : null });
	}

	protected alElegirTipo(valor: string): void {
		this.filtrar({ tipo: esTipo(valor) ? valor : null });
	}

	// HU-18 RF-21: sin confirmación, porque solo clasifica la cuenta para los reportes y se deshace igual. Si el
	// backend lo rechaza, el selector vuelve al tipo que la cuenta sigue teniendo
	protected async alElegirTipoDe(usuario: UsuarioResumen, selector: HTMLSelectElement): Promise<void> {
		const tipo = selector.value;
		// Mientras guarda no se deshabilita (el foco caería al cuerpo de la página): lo elegido vuelve a lo de la cuenta
		if (this.procesando().has(usuario.idUsuario)) {
			selector.value = usuario.tipoCliente;
			return;
		}
		if (!esTipo(tipo) || tipo === usuario.tipoCliente) {
			return;
		}
		const cambiado = await this.aplicar(
			usuario,
			() => this.usuarios.cambiarTipo(usuario.idUsuario, tipo),
			(actualizado) => `${nombreDe(actualizado)} ahora es ${NOMBRES_DE_TIPO[actualizado.tipoCliente]}.`,
		);
		if (!cambiado) {
			selector.value = usuario.tipoCliente;
		}
	}

	protected irAPagina(pagina: number): void {
		// Mientras carga, o antes de la primera o después de la última, el toque se ignora: el botón no se
		// deshabilita, para no perder el foco (revisión total)
		const total = this.pagina()?.totalPaginas ?? 0;
		if (this.listado.isLoading() || pagina < 0 || (pagina > 0 && pagina >= total)) {
			return;
		}
		this.navegar({ ...this.filtros(), pagina });
	}

	// RF-7, con confirmación. Si se cancela o el backend lo rechaza, el selector
	// vuelve al rol que la cuenta sigue teniendo.
	protected async alElegirRolDe(usuario: UsuarioResumen, selector: HTMLSelectElement): Promise<void> {
		const rol = selector.value;
		if (this.procesando().has(usuario.idUsuario)) {
			selector.value = usuario.rol;
			return;
		}
		if (!esRol(rol) || rol === usuario.rol) {
			return;
		}
		const confirmado = await this.dialogo().abrir({
			titulo: 'Cambiar tipo de cuenta',
			mensaje:
				`${nombreDe(usuario)} (${usuario.correo}) pasará de ${NOMBRES_DE_ROL[usuario.rol]} ` +
				`a ${NOMBRES_DE_ROL[rol]}. El cambio rige desde su siguiente acción en el sistema.`,
			confirmar: 'Cambiar',
		});
		const cambiado =
			confirmado &&
			(await this.aplicar(
				usuario,
				() => this.usuarios.cambiarRol(usuario.idUsuario, rol),
				(actualizado) => `${nombreDe(actualizado)} ahora es ${NOMBRES_DE_ROL[actualizado.rol]}.`,
			));
		if (!cambiado) {
			selector.value = usuario.rol;
		}
	}

	// Desactivar corta el acceso y pide confirmación (RF-10); reactivar solo lo
	// devuelve, así que no pregunta (RF-11)
	protected async alCambiarEstadoDe(usuario: UsuarioResumen): Promise<void> {
		if (this.procesando().has(usuario.idUsuario)) {
			return;
		}
		const desactivar = usuario.estado === 'ACTIVA';
		if (desactivar) {
			const confirmado = await this.dialogo().abrir({
				titulo: 'Desactivar cuenta',
				mensaje: `${nombreDe(usuario)} (${usuario.correo}) no podrá iniciar sesión hasta que la reactive.`,
				confirmar: 'Desactivar',
			});
			if (!confirmado) {
				return;
			}
		}
		await this.aplicar(
			usuario,
			() => this.usuarios.cambiarEstado(usuario.idUsuario, desactivar ? 'DESACTIVADA' : 'ACTIVA'),
			(actualizado) =>
				`La cuenta de ${nombreDe(actualizado)} quedó ${desactivar ? 'desactivada' : 'reactivada'}.`,
		);
	}

	protected nombreDe(usuario: UsuarioResumen): string {
		return nombreDe(usuario);
	}

	// La fila se actualiza con lo que devolvió el backend, sin volver a pedir la
	// lista; si lo rechaza, queda como estaba y se explica el motivo
	private async aplicar(
		usuario: UsuarioResumen,
		operacion: () => Promise<UsuarioResumen>,
		exito: (actualizado: UsuarioResumen) => string,
	): Promise<boolean> {
		this.procesando.update((ids) => new Set(ids).add(usuario.idUsuario));
		this.avisoDeError.set('');
		this.avisoDeExito.set('');
		try {
			const actualizado = await operacion();
			this.pagina.update((pagina) =>
				pagina === undefined
					? pagina
					: {
							...pagina,
							contenido: pagina.contenido.map((cada) =>
								cada.idUsuario === actualizado.idUsuario ? actualizado : cada,
							),
						},
			);
			this.avisoDeExito.set(exito(actualizado));
			return true;
		} catch (error: unknown) {
			this.avisoDeError.set(mensajeDeError(comoError(error)));
			return false;
		} finally {
			this.procesando.update((ids) => {
				const restantes = new Set(ids);
				restantes.delete(usuario.idUsuario);
				return restantes;
			});
		}
	}

	// Cambiar un filtro cambia la lista entera: se vuelve a la primera página
	private filtrar(cambios: Partial<FiltrosDeUsuarios>): void {
		this.navegar({ ...this.filtros(), ...cambios, pagina: 0 });
	}

	private navegar(filtros: FiltrosDeUsuarios): void {
		void this.router.navigate([], { relativeTo: this.ruta, queryParams: aConsulta(filtros) });
	}

	private cancelarEspera(): void {
		if (this.espera !== null) {
			clearTimeout(this.espera);
			this.espera = null;
		}
	}
}

function nombreDe(usuario: UsuarioResumen): string {
	return `${usuario.nombre} ${usuario.apellido}`;
}


function esRol(valor: string | null): valor is Rol {
	return (ROLES as readonly string[]).includes(valor ?? '');
}

function esEstado(valor: string | null): valor is EstadoCuenta {
	return (ESTADOS as readonly string[]).includes(valor ?? '');
}

function esTipo(valor: string | null): valor is TipoCliente {
	return (TIPOS_DE_CLIENTE as readonly string[]).includes(valor ?? '');
}

// La URL la puede escribir cualquiera: lo que no es válido se descarta en vez
// de llegar al backend y volver como un 400
function leerFiltros(consulta: ParamMap): FiltrosDeUsuarios {
	const rol = consulta.get('rol');
	const estado = consulta.get('estado');
	const tipo = consulta.get('tipo');
	return {
		rol: esRol(rol) ? rol : null,
		estado: esEstado(estado) ? estado : null,
		tipo: esTipo(tipo) ? tipo : null,
		texto: consulta.get('texto') ?? '',
		pagina: paginaDeLaUrl(consulta.get('pagina')),
	};
}

// Solo lo que filtra queda en la URL, así la lista sin filtros es /usuarios
function aConsulta(filtros: FiltrosDeUsuarios): Record<string, string | number | undefined> {
	const texto = filtros.texto.trim();
	return {
		rol: filtros.rol ?? undefined,
		estado: filtros.estado ?? undefined,
		tipo: filtros.tipo ?? undefined,
		texto: texto === '' ? undefined : texto,
		pagina: filtros.pagina > 0 ? filtros.pagina : undefined,
	};
}
