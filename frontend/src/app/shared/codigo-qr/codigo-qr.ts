import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import qrcode from 'qrcode-generator';

// La zona silenciosa que exige el estándar: 4 módulos claros alrededor, para
// que el lector encuentre el código
const MARGEN = 4;
// Píxeles por módulo en la imagen que se descarga: nítida al leerla desde la galería
const ESCALA_PNG = 12;
export const ESPERA_PARA_LIBERAR = 10_000;

interface Dibujo {
	lado: number;
	trazo: string;
}

interface Modulos {
	// Con la zona silenciosa
	lado: number;
	// Fila y columna de cada módulo oscuro, ya desplazadas por la zona silenciosa
	oscuros: [number, number][];
}

// Corrección de errores M; el tamaño lo elige la librería según el largo del texto
function modulos(valor: string): Modulos | null {
	if (valor === '') {
		return null;
	}
	const qr = qrcode(0, 'M');
	qr.addData(valor);
	qr.make();
	const cantidad = qr.getModuleCount();
	const oscuros: [number, number][] = [];
	for (let fila = 0; fila < cantidad; fila++) {
		for (let columna = 0; columna < cantidad; columna++) {
			if (qr.isDark(fila, columna)) {
				oscuros.push([fila + MARGEN, columna + MARGEN]);
			}
		}
	}
	return { lado: cantidad + 2 * MARGEN, oscuros };
}

// Baja el QR como PNG, negro sobre blanco en cualquier tema: para pagarlo desde
// la app del banco con la imagen guardada. Se dibuja en un canvas con los
// mismos módulos que la pantalla, sin pasar por el SVG
export async function descargarQr(valor: string, nombreDelArchivo: string): Promise<void> {
	const qr = modulos(valor);
	if (!qr) {
		return;
	}
	const lienzo = document.createElement('canvas');
	lienzo.width = lienzo.height = qr.lado * ESCALA_PNG;
	const contexto = lienzo.getContext('2d');
	if (!contexto) {
		return;
	}
	contexto.fillStyle = '#ffffff';
	contexto.fillRect(0, 0, lienzo.width, lienzo.height);
	contexto.fillStyle = '#000000';
	for (const [fila, columna] of qr.oscuros) {
		contexto.fillRect(columna * ESCALA_PNG, fila * ESCALA_PNG, ESCALA_PNG, ESCALA_PNG);
	}
	const imagen = await new Promise<Blob | null>((listo) => lienzo.toBlob(listo, 'image/png'));
	if (!imagen) {
		return;
	}
	const direccion = URL.createObjectURL(imagen);
	const enlace = document.createElement('a');
	enlace.href = direccion;
	enlace.download = nombreDelArchivo;
	enlace.click();
	// No en el acto: Firefox y Safari pueden cortar la descarga si la dirección se libera antes de leerla
	setTimeout(() => URL.revokeObjectURL(direccion), ESPERA_PARA_LIBERAR);
}

// Un código QR dibujado a partir de un texto: el de un cobro
// (HU-13 RF-1). La librería calcula qué módulos van oscuros y el componente los
// dibuja como un <svg> con un solo <path>, sin innerHTML.
@Component({
	selector: 'app-codigo-qr',
	template: `
		@if (dibujo(); as qr) {
			<svg
				role="img"
				[attr.aria-label]="etiqueta()"
				[attr.viewBox]="'0 0 ' + qr.lado + ' ' + qr.lado"
				shape-rendering="crispEdges"
			>
				<rect class="claro" width="100%" height="100%" />
				<path class="oscuro" [attr.d]="qr.trazo" />
			</svg>
		}
	`,
	styleUrl: './codigo-qr.scss',
	changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CodigoQr {
	readonly valor = input.required<string>();
	// El nombre accesible: de qué es el código
	readonly etiqueta = input.required<string>();

	protected readonly dibujo = computed((): Dibujo | null => {
		const qr = modulos(this.valor());
		if (!qr) {
			return null;
		}
		const trazo = qr.oscuros.map(([fila, columna]) => `M${columna} ${fila}h1v1h-1z`).join('');
		return { lado: qr.lado, trazo };
	});
}
