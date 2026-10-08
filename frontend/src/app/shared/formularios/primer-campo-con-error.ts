// Qué campo recibe el foco cuando falla el envío: el primero con error, en el
// orden en que aparece en la pantalla, para no dejar a quien usa teclado o
// lector de pantalla buscando qué falló.
export function primerCampoConError<C extends string>(
	orden: readonly C[],
	invalidos: Record<C, boolean>,
): C | null {
	return orden.find((campo) => invalidos[campo]) ?? null;
}

// Lo mismo con lo que rechazó el backend (los `errores` de un DATOS_INVALIDOS):
// sin esto el foco queda en el botón y el lector de pantalla solo anuncia el
// aviso general, no qué campo corregir.
export function primerCampoRechazado<C extends string>(
	orden: readonly C[],
	errores: readonly { campo: string; mensaje: string }[] | undefined,
): C | null {
	return orden.find((campo) => errores?.some((error) => error.campo === campo)) ?? null;
}

// Lleva el foco al campo con ese id dentro de la pantalla; sin campo, no hace nada
export function enfocarCampo(contenedor: HTMLElement, id: string | null): void {
	if (id !== null) {
		contenedor.querySelector<HTMLElement>(`#${id}`)?.focus();
	}
}
