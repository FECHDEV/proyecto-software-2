// Precios en bolivianos, con Intl y no armado a mano: separa los miles con punto
// y los decimales con coma, como se leen en Bolivia
const PRECIO = new Intl.NumberFormat('es-BO', { style: 'currency', currency: 'BOB' });

export function precio(valor: number): string {
	return PRECIO.format(valor);
}

// Cantidades: separa los miles como se leen en Bolivia («5.000»)
const CANTIDAD = new Intl.NumberFormat('es-BO');

export function cantidad(valor: number): string {
	return CANTIDAD.format(valor);
}
