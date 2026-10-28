// El nombre del producto vive solo acá: cambiarlo no toca ninguna pantalla.
// En la interfaz no se menciona la universidad ni que es un trabajo académico
// (docs/decisiones.md → «La aplicación es un producto, no un trabajo académico»).
export const MARCA = {
	nombre: 'Tienda Montero',
	inicial: 'M',
	// La que muestran los buscadores cuando la pantalla no tiene una propia. La
	// misma frase va fija en index.html, para las tarjetas de los enlaces
	descripcion:
		'Ferretería de Montero: herramientas, hogar y jardín. Vea si hay stock, compre en línea y retire en la tienda.',
} as const;
