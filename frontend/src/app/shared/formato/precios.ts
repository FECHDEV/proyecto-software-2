// Precios en bolivianos, con Intl y no armado a mano: separa los miles con punto
// y los decimales con coma, como se leen en Bolivia
const PRECIO = new Intl.NumberFormat('es-BO', { style: 'currency', currency: 'BOB' });

export function precio(valor: number): string {
	return PRECIO.format(valor);
}

// El precio como lo escribe una persona: «420», «420,5» o «420.50», hasta
// 99 999,99. Sin separador de miles, para que «1.250» no sea ambiguo. null si
// no es un precio que acepte el backend (cero incluido: lo explica problemaDelPrecio)
export function leerPrecio(texto: string): number | null {
	const limpio = texto.trim();
	return /^\d{1,5}([.,]\d{1,2})?$/.test(limpio) ? Number(limpio.replace(',', '.')) : null;
}

// Por qué lo escrito no es un precio válido (HU-07 RF-3); null si lo es o si
// está vacío, que es cosa de la regla de obligatorio. El costo sigue las mismas
// reglas con su nombre (HU-18 RF-19)
export function problemaDelPrecio(texto: string, nombre: 'precio' | 'costo' = 'precio'): string | null {
	const limpio = texto.trim();
	if (limpio === '') {
		return null;
	}
	const leido = leerPrecio(limpio);
	if (leido !== null) {
		return leido > 0 ? null : `El ${nombre} tiene que ser mayor que 0.`;
	}
	// La coma siempre separa decimales; el punto seguido de grupos de tres
	// cifras se lee como separador de miles («1.250»)
	if (/^\d+(,\d{3,}|\.\d{4,})$/.test(limpio) || /^0\.\d{3}$/.test(limpio)) {
		return `El ${nombre} admite hasta dos decimales.`;
	}
	if (/^[1-9]\d{0,2}(\.\d{3})+(,\d{1,2})?$/.test(limpio)) {
		return `Escriba el ${nombre} sin separador de miles, por ejemplo 1234,50.`;
	}
	if (/^\d+([.,]\d{1,2})?$/.test(limpio)) {
		return `El ${nombre} admite hasta Bs 99.999,99.`;
	}
	return `Escriba el ${nombre} con números, por ejemplo 420,50.`;
}

// Cantidades: separa los miles como se leen en Bolivia («5.000»)
const CANTIDAD = new Intl.NumberFormat('es-BO');

export function cantidad(valor: number): string {
	return CANTIDAD.format(valor);
}
