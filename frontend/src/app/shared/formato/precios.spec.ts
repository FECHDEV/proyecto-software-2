import { cantidad, leerPrecio, precio, problemaDelPrecio } from './precios';

// Intl separa «Bs» del número con un espacio que no corta (U+00A0)
const ESPACIO = ' ';

describe('precio', () => {
  it('muestra el monto en bolivianos con dos decimales', () => {
    expect(precio(50)).toBe(`Bs${ESPACIO}50,00`);
  });

  it('separa los miles con punto y los decimales con coma', () => {
    expect(precio(1234.5)).toBe(`Bs${ESPACIO}1.234,50`);
  });

  // El máximo que acepta el backend para un producto (HU-07 RF-3)
  it('muestra el precio más alto permitido', () => {
    expect(precio(99999.99)).toBe(`Bs${ESPACIO}99.999,99`);
  });
});

// HU-07: el precio como lo escribe el Administrador
describe('leerPrecio', () => {
  it('acepta coma o punto para los decimales', () => {
    expect(leerPrecio('420,5')).toBe(420.5);
    expect(leerPrecio(' 420.50 ')).toBe(420.5);
    expect(leerPrecio('420')).toBe(420);
    expect(leerPrecio('99999,99')).toBe(99999.99);
  });

  // Sin separador de miles: «1.250» sería mil doscientos cincuenta o uno con veinticinco
  it('no es un precio con tres decimales, separador de miles, signo o letras', () => {
    for (const texto of ['12,345', '1.250', '1.234,50', '-5', '4e2', 'abc', '', '100000']) {
      expect(leerPrecio(texto)).toBeNull();
    }
  });
});

// HU-07 RF-3: el motivo que se muestra bajo el precio
describe('problemaDelPrecio', () => {
  it('no hay problema con un precio válido ni con el campo vacío (eso lo dice «obligatorio»)', () => {
    expect(problemaDelPrecio('420,50')).toBeNull();
    expect(problemaDelPrecio('')).toBeNull();
  });

  it('explica cada caso', () => {
    expect(problemaDelPrecio('0')).toBe('El precio tiene que ser mayor que 0.');
    expect(problemaDelPrecio('0,00')).toBe('El precio tiene que ser mayor que 0.');
    expect(problemaDelPrecio('12,345')).toBe('El precio admite hasta dos decimales.');
    expect(problemaDelPrecio('0.125')).toBe('El precio admite hasta dos decimales.');
    expect(problemaDelPrecio('100000')).toBe('El precio admite hasta Bs 99.999,99.');
    expect(problemaDelPrecio('1.234,50')).toBe('Escriba el precio sin separador de miles, por ejemplo 1234,50.');
    expect(problemaDelPrecio('abc')).toBe('Escriba el precio con números, por ejemplo 420,50.');
  });

  // HU-18 RF-19: el costo tiene las mismas reglas y sus propios mensajes
  it('nombra al campo que explica', () => {
    expect(problemaDelPrecio('0', 'costo')).toBe('El costo tiene que ser mayor que 0.');
    expect(problemaDelPrecio('12,345', 'costo')).toBe('El costo admite hasta dos decimales.');
    expect(problemaDelPrecio('abc', 'costo')).toBe('Escriba el costo con números, por ejemplo 420,50.');
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
