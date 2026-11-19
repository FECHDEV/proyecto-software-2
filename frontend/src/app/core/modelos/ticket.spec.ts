import { LineaDePedido } from './pedido';
import { ahorroDelTicket, fechaYHora, lineasDelTicket, pagosDelTicket } from './ticket';

// Lo que comparten los dos comprobantes (HU-23 RF-15, HU-14 RF-10)
describe('ticket', () => {
  // El formato de precios separa «Bs» con un espacio duro
  const sinEspaciosDuros = (texto: string): string => texto.replace(/\s+/g, ' ');

  it('fechaYHora: la fecha corta y la hora del servidor', () => {
    expect(fechaYHora('2026-11-17T10:30:45')).toBe('17/11/2026 10:30');
  });

  it('lineasDelTicket: cantidad, unidad y precio, y el importe', () => {
    const [linea] = lineasDelTicket([
      { idProducto: 2, nombre: 'Cable', unidad: 'METRO', precio: 4.5, precioNormal: null, descuento: null, descontado: null, cantidad: 2.5, subtotal: 11.25 },
    ]);

    expect(linea.nombre).toBe('Cable');
    expect(sinEspaciosDuros(linea.detalle)).toBe('2,5 metros × Bs 4,50');
    expect(sinEspaciosDuros(linea.importe)).toBe('Bs 11,25');
  });

  // HU-24 RF-18: el rastrillo de Bs 100,00 con −15 %
  const RASTRILLO: LineaDePedido = {
    idProducto: 9, nombre: 'Rastrillo', unidad: 'UNIDAD', precio: 85, precioNormal: 100, descuento: 15, descontado: 30, cantidad: 2, subtotal: 170,
  };
  const CABLE: LineaDePedido = {
    idProducto: 2, nombre: 'Cable', unidad: 'METRO', precio: 4.5, precioNormal: null, descuento: null, descontado: null, cantidad: 2.5, subtotal: 11.25,
  };

  it('RF-18: la línea con descuento lleva «(−15 %)»', () => {
    const [rastrillo, cable] = lineasDelTicket([RASTRILLO, CABLE]);

    expect(sinEspaciosDuros(rastrillo.detalle)).toBe('2 unidades × Bs 85,00 (−15 %)');
    expect(sinEspaciosDuros(cable.detalle)).toBe('2,5 metros × Bs 4,50');
  });

  // Revisión de interfaz: al imprimir en 80 mm, «(−53 %)» se partía antes del «%»
  it('RF-18: «(−15 %)» no se parte al imprimir', () => {
    const [rastrillo] = lineasDelTicket([RASTRILLO]);

    expect(rastrillo.detalle.endsWith(String.fromCharCode(0xa0) + '(−15' + String.fromCharCode(0x202f) + '%)')).toBe(true);
  });

  it('RF-18: una línea sin los campos del descuento (respuesta vieja) no dice «undefined»', () => {
    const viejo = { ...CABLE, descuento: undefined, descontado: undefined } as unknown as LineaDePedido;

    expect(lineasDelTicket([viejo])[0].detalle).not.toContain('undefined');
    expect(ahorroDelTicket([viejo])).toBeNull();
  });

  it('RF-18: el ahorro suma en centavos sin error de coma flotante', () => {
    const ahorro = ahorroDelTicket([
      { ...RASTRILLO, descontado: 0.1 },
      { ...RASTRILLO, descontado: 0.2 },
      CABLE,
    ]);

    expect(sinEspaciosDuros(ahorro!)).toBe('Bs 0,30');
    expect(sinEspaciosDuros(ahorroDelTicket([RASTRILLO])!)).toBe('Bs 30,00');
  });

  it('RF-18: las líneas viejas sin descuento no cambian ni ahorran', () => {
    expect(ahorroDelTicket([CABLE])).toBeNull();
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
