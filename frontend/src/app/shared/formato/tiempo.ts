// El tiempo que queda como reloj «mm:ss»: el de la página del pedido (HU-12
// RF-8) y el del pedido pendiente en «Mis pedidos» (HU-14 RF-2). Nunca
// negativo; más de 99 minutos se muestran tal cual («120:00»)
export function comoReloj(segundos: number): string {
	const total = Math.max(0, Math.floor(segundos));
	return `${dosCifras(Math.floor(total / 60))}:${dosCifras(total % 60)}`;
}

function dosCifras(numero: number): string {
	return `${numero}`.padStart(2, '0');
}
