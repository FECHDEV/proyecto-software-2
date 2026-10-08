// El id de un registro, leído de la URL. Los ids del backend son Long
// autoincrementales que empiezan en 1; se aceptan hasta 15 dígitos, que es lo
// que entra sin redondeo en un número de JavaScript. Lo demás no es un id y no
// llega al backend.
export function idDeLaUrl(valor: string | null): number | null {
	return valor !== null && /^[1-9]\d{0,14}$/.test(valor) ? Number(valor) : null;
}
