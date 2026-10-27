import { descripcionDeMovimiento, Movimiento } from './inventario';

function movimiento(cambios: Partial<Movimiento>): Movimiento {
  return {
    idMovimiento: 1,
    fecha: '2026-10-19T10:00:00',
    tipo: 'ENTRADA',
    motivo: null,
    cantidad: 10,
    stockResultante: 10,
    usuario: 'Ana Paz',
    nota: null,
    idPedido: null,
    contado: null,
    comprometido: null,
    ...cambios,
  };
}

describe('descripcionDeMovimiento', () => {
  // HU-08 RF-11
  it('nombra la entrada y el ajuste con su motivo', () => {
    expect(descripcionDeMovimiento(movimiento({}))).toBe('Entrada');
    expect(descripcionDeMovimiento(movimiento({ tipo: 'AJUSTE', motivo: 'MERMA' }))).toBe('Ajuste · Daño o merma');
  });

  // HU-12 RF-14
  it('los movimientos de pedido tienen su texto con el número', () => {
    expect(descripcionDeMovimiento(movimiento({ tipo: 'RESERVA', cantidad: -2, idPedido: 12 }))).toBe(
      'Reserva por pedido N.º 12',
    );
    expect(descripcionDeMovimiento(movimiento({ tipo: 'LIBERACION', cantidad: 2, idPedido: 12 }))).toBe(
      'Liberación por pedido N.º 12',
    );
  });
});
