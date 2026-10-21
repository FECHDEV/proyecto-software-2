import {
	afterNextRender,
	ChangeDetectionStrategy,
	Component,
	computed,
	ElementRef,
	inject,
	Injector,
	signal,
	viewChild,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { mensajeDeError } from '../../core/api/mensajes-error';
import { comoError, ErrorApi } from '../../core/modelos/error-api';

type Estado = 'verificando' | 'verificado' | 'fallido';

const ENLACE_INVALIDO: ErrorApi = { status: 400, detail: 'El enlace no sirve', codigo: 'TOKEN_VERIFICACION_INVALIDO' };

// El enlace del correo «Confirme su correo» (HU-20). Funciona con sesión y sin
// ella, y no la inicia (RF-4): verifica al abrir y dice cómo salió
@Component({
	selector: 'app-verificacion',
	imports: [RouterLink],
	templateUrl: './verificacion.page.html',
	styleUrl: './verificacion.page.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerificacionPage {
	private readonly autenticacion = inject(AutenticacionService);
	private readonly injector = inject(Injector);
	private readonly resultado = viewChild<ElementRef<HTMLElement>>('resultado');

	// El token llega en el enlace y no se muestra nunca (RF-16). Recortado: un
	// enlace cortado que solo trae espacios es un enlace sin token
	private readonly token = (inject(ActivatedRoute).snapshot.queryParamMap.get('token') ?? '').trim();

	protected readonly estado = signal<Estado>('verificando');
	private readonly error = signal<ErrorApi | null>(null);
	// Un problema de red o del servidor se arregla reintentando. Cualquier otro
	// rechazo es del enlace, y dice siempre lo mismo (RF-5)
	protected readonly sePuedeReintentar = computed(() => {
		const status = this.error()?.status ?? 0;
		return status === 0 || status >= 500;
	});
	protected readonly aviso = computed(() =>
		this.sePuedeReintentar() ? mensajeDeError(this.error()) : mensajeDeError(ENLACE_INVALIDO),
	);

	constructor() {
		if (this.token === '') {
			// Mismo mensaje que un enlace vencido o ya usado (RF-5)
			this.fallar(ENLACE_INVALIDO);
			return;
		}
		void this.verificar();
	}

	// «Reintentar» desaparece mientras verifica: el foco va al resultado, para no perderse
	protected async reintentar(): Promise<void> {
		await this.verificar();
		afterNextRender(() => this.resultado()?.nativeElement.focus(), { injector: this.injector });
	}

	private async verificar(): Promise<void> {
		this.estado.set('verificando');
		this.error.set(null);
		try {
			await this.autenticacion.verificarCorreo(this.token);
			this.estado.set('verificado');
		} catch (error: unknown) {
			this.fallar(comoError(error));
		}
	}

	private fallar(error: ErrorApi): void {
		this.error.set(error);
		this.estado.set('fallido');
	}
}
