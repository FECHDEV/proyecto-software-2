import { Directive, input } from '@angular/core';

// Un campo de archivo mientras sube lo elegido: va con aria-disabled y no disabled, así el foco se queda ahí si la
// subida falla, y no abre el selector (aria-disabled solo no lo detiene). Quien lo usa ignora también el change
// mientras sube. Los estilos, en _archivo.scss
@Directive({
	selector: 'input[type="file"][ocupado]',
	host: {
		'[attr.aria-disabled]': 'ocupado() ? "true" : null',
		'(click)': 'alPulsar($event)',
	},
})
export class ArchivoOcupado {
	readonly ocupado = input.required<boolean>();

	protected alPulsar(evento: Event): void {
		if (this.ocupado()) {
			evento.preventDefault();
		}
	}
}
