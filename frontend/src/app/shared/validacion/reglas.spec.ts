import { bytesUtf8, esFechaFutura, fechaLocalISO } from './reglas';

describe('bytesUtf8', () => {
  it('cuenta un byte por carácter ASCII', () => {
    expect(bytesUtf8('secreta12')).toBe(9);
  });

  // El backend limita la contraseña por bytes, no por caracteres: BCrypt corta
  // a los 72 bytes
  it('cuenta los caracteres multibyte por lo que ocupan', () => {
    expect(bytesUtf8('ñ')).toBe(2);
    expect(bytesUtf8('á')).toBe(2);
    expect(bytesUtf8('🎉')).toBe(4);
  });

  it('acepta el borde de 72 bytes y detecta el de 73', () => {
    expect(bytesUtf8('a'.repeat(72))).toBe(72);
    expect(bytesUtf8('a'.repeat(73))).toBe(73);
    expect(bytesUtf8(`${'a'.repeat(70)}ñ`)).toBe(72);
    expect(bytesUtf8(`${'a'.repeat(71)}ñ`)).toBe(73);
  });

  it('una cadena vacía no ocupa nada', () => {
    expect(bytesUtf8('')).toBe(0);
  });
});

describe('esFechaFutura', () => {
  const HOY = new Date(2026, 8, 17, 15, 30);

  // RF-13: solo se rechaza la fecha posterior al día en curso
  it('la fecha de hoy no es futura', () => {
    expect(esFechaFutura('2026-09-17', HOY)).toBe(false);
  });

  it('una fecha pasada no es futura', () => {
    expect(esFechaFutura('2000-05-20', HOY)).toBe(false);
  });

  it('el día siguiente sí es futuro', () => {
    expect(esFechaFutura('2026-09-18', HOY)).toBe(true);
  });

  it('una fecha vacía no se trata como futura, de eso se ocupa el campo obligatorio', () => {
    expect(esFechaFutura('', HOY)).toBe(false);
  });
});

describe('fechaLocalISO', () => {
  // El <input type="date"> trabaja en la zona horaria de quien lo usa: si la
  // fecha se armara en UTC, un 17 a la noche en Bolivia saldría como 18
  it('usa el día local, no el de UTC', () => {
    expect(fechaLocalISO(new Date(2026, 8, 17, 22, 0))).toBe('2026-09-17');
  });

  it('rellena mes y día con cero', () => {
    expect(fechaLocalISO(new Date(2026, 0, 5))).toBe('2026-01-05');
  });
});
