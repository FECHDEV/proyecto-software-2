// Si los datos de un formulario coinciden con una referencia (lo enviado o lo
// guardado). Sirve para saber si un error del backend o una confirmación
// todavía hablan de lo que hay en pantalla: en cuanto se edita, dejan de valer.
// Sin referencia nunca son iguales.
export function iguales<T extends object>(referencia: T | null, actuales: T): boolean {
	if (referencia === null) {
		return false;
	}
	return (Object.keys(referencia) as (keyof T)[]).every((clave) => referencia[clave] === actuales[clave]);
}
