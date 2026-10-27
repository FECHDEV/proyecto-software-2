import { inject, signal } from '@angular/core';

import { CuentaService } from '../api/cuenta.service';
import { mensajeDeError } from '../api/mensajes-error';
import { comoError } from '../modelos/error-api';

// «Reenviar el correo» de HU-20 (RF-9), en el carrito y en «Mis datos» (revisión total: estaba copiado). Mientras
// envía, el botón no se deshabilita (no pierde el foco) y un segundo toque no manda otro correo. Si ya se verificó en
// otra pestaña, lo dice y avisa a la pantalla (RF-11)
export function reenvioDeVerificacion(alYaVerificado: () => void) {
	const cuenta = inject(CuentaService);
	const enviando = signal(false);
	const resultado = signal('');
	return {
		enviando: enviando.asReadonly(),
		resultado: resultado.asReadonly(),
		async reenviar(): Promise<void> {
			if (enviando()) {
				return;
			}
			enviando.set(true);
			resultado.set('');
			try {
				resultado.set(await cuenta.reenviarVerificacion());
			} catch (error: unknown) {
				const problema = comoError(error);
				resultado.set(mensajeDeError(problema));
				if (problema.codigo === 'CORREO_YA_VERIFICADO') {
					alYaVerificado();
				}
			}
			enviando.set(false);
		},
	};
}
