// La página de una lista, leída de la URL. La URL la puede escribir cualquiera:
// solo se aceptan dígitos y un tamaño que entre en el int del backend, así
// «1e20» o «-3» caen en la primera página en vez de volver como un 400.
export function paginaDeLaUrl(valor: string | null): number {
	return valor !== null && /^\d{1,6}$/.test(valor) ? Number(valor) : 0;
}
