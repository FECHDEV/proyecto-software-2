import { SchemaPath, validate } from '@angular/forms/signals';

// Un dato de solo espacios es un dato sin llenar, igual que uno vacío: el
// backend lo rechaza con @NotBlank y acá se frena antes de ir.
export function sinEspaciosSolos(ruta: SchemaPath<string>, message: string): void {
	validate(ruta, ({ value }) =>
		value() !== '' && value().trim() === '' ? { kind: 'soloEspacios', message } : null,
	);
}
