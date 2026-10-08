import { cantidad, precio } from './precios';

// Intl separa «Bs» del número con un espacio que no corta (U+00A0)
const ESPACIO = ' ';

describe('precio', () => {
  it('muestra el monto en bolivianos con dos decimales', () => {
    expect(precio(50)).toBe(`Bs${ESPACIO}50,00`);
  });

  it('separa los miles con punto y los decimales con coma', () => {
    expect(precio(1234.5)).toBe(`Bs${ESPACIO}1.234,50`);
  });

  // El máximo que acepta el backend (DECIMAL(10,2))
  it('muestra el precio más alto permitido', () => {
    expect(precio(99999999.99)).toBe(`Bs${ESPACIO}99.999.999,99`);
  });
});

// Las cantidades de entradas separan los miles como se leen en Bolivia
describe('cantidad', () => {
  it('separa los miles con punto', () => {
    expect(cantidad(5000)).toBe('5.000');
    expect(cantidad(1234567)).toBe('1.234.567');
  });

  it('deja igual una cantidad chica', () => {
    expect(cantidad(40)).toBe('40');
    expect(cantidad(0)).toBe('0');
  });
});
