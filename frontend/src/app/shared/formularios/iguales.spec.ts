import { iguales } from './iguales';

// Compara el formulario con lo enviado o lo guardado: sirve para saber si un
// error del backend o una confirmación todavía hablan de lo que hay en pantalla
describe('iguales', () => {
  it('son iguales si coinciden todos los campos de la referencia', () => {
    expect(iguales({ nombre: 'Ana', telefono: '' }, { nombre: 'Ana', telefono: '' })).toBe(true);
  });

  it('un campo distinto los diferencia', () => {
    expect(iguales({ nombre: 'Ana', telefono: '' }, { nombre: 'Ana', telefono: '7' })).toBe(false);
  });

  // Sin referencia (todavía no se envió ni guardó nada) nunca son iguales
  it('sin referencia no son iguales', () => {
    expect(iguales(null, { nombre: 'Ana' })).toBe(false);
  });
});
