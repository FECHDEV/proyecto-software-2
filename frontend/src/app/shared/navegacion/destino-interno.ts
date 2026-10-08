// A dónde volver después de entrar o de crear la cuenta. El guard guarda en
// `destino` la ruta que la persona quería; se acepta solo si es del sitio
// (empieza con una sola barra), para no convertirlo en un redirect abierto.
export function destinoInterno(pedido: string | null): string | null {
	return pedido !== null && pedido.startsWith('/') && !pedido.startsWith('//') ? pedido : null;
}

// El destino que se le pasa a otra pantalla del circuito (iniciar sesión, crear
// cuenta) como parámetros de la URL; vacío si no hay o no es del sitio
export function parametrosDelDestino(pedido: string | null): Record<string, string> {
	const destino = destinoInterno(pedido);
	return destino === null ? {} : { destino };
}
