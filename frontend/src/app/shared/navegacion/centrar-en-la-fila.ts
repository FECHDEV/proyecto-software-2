// En una fila que se desplaza de costado (la navegación del personal en el celular), lleva el elemento al centro sin
// mover la página: con scrollIntoView el navegador también podía bajarla (revisión total, 26/10/2026)
export function centrarEnLaFila(fila: HTMLElement, elemento: HTMLElement): void {
	const izquierda = elemento.offsetLeft - fila.offsetLeft - (fila.clientWidth - elemento.offsetWidth) / 2;
	fila.scrollLeft = Math.max(0, izquierda);
}
