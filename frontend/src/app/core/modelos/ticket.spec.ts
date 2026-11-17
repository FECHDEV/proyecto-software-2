import { fechaYHora, lineasDelTicket, pagosDelTicket } from './ticket';

// Lo que comparten los dos comprobantes (HU-23 RF-15, HU-14 RF-10)
describe('ticket', () => {
  // El formato de precios separa «Bs» con un espacio duro
  const sinEspaciosDuros = (texto: string): string => texto.replace(/\s+/g, ' ');

  it('fechaYHora: la fecha corta y la hora del servidor', () => {
    expect(fechaYHora('2026-11-17T10:30:45')).toBe('17/11/2026 10:30');
  });

  it('lineasDelTicket: cantidad, unidad y precio, y el importe', () => {
    const [linea] = lineasDelTicket([
      { idProducto: 2, nombre: 'Cable', unidad: 'METRO', precio: 4.5, cantidad: 2.5, subtotal: 11.25 },
    ]);

    expect(linea.nombre).toBe('Cable');
    expect(sinEspaciosDuros(linea.detalle)).toBe('2,5 metros × Bs 4,50');
    expect(sinEspaciosDuros(linea.importe)).toBe('Bs 11,25');
  });

  it('pagosDelTicket: en efectivo, el medio, lo recibido y el cambio, en ese orden', () => {
    const pagos = pagosDelTicket('EFECTIVO', 200, 29).map(({ etiqueta, valor }) => [etiqueta, sinEspaciosDuros(valor)]);

    expect(pagos).toEqual([
      ['Pago', 'Efectivo'],
      ['Recibido', 'Bs 200,00'],
      ['Cambio', 'Bs 29,00'],
    ]);
  });

  it('pagosDelTicket: con QR, solo el medio', () => {
    expect(pagosDelTicket('QR', null, null)).toEqual([{ etiqueta: 'Pago', valor: 'QR' }]);
  });

  // HU-14 RF-10: en línea la tienda no sabe con qué se pagó en el sistema de pago
  it('pagosDelTicket: sin medio dice «En línea»', () => {
    expect(pagosDelTicket(null, null, null)).toEqual([{ etiqueta: 'Pago', valor: 'En línea' }]);
  });
});
