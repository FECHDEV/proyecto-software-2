import { fechaCorta, horaCorta } from './fechas';

// LocalTime llega del backend como HH:mm:ss; en pantalla sobran los segundos
describe('horaCorta', () => {
  it('muestra horas y minutos', () => {
    expect(horaCorta('22:00:00')).toBe('22:00');
  });

  it('deja igual una hora que ya viene sin segundos', () => {
    expect(horaCorta('09:30')).toBe('09:30');
  });
});

describe('fechaCorta', () => {
  it('muestra una fecha del backend como día, mes y año', () => {
    expect(fechaCorta('2000-05-20')).toBe('20/05/2000');
  });

  // LocalDateTime del backend: la hora no importa para mostrar el día
  it('ignora la hora de una fecha con hora', () => {
    expect(fechaCorta('2026-09-14T23:30:00')).toBe('14/09/2026');
  });

  // Un 1 de enero no puede mostrarse como 31 de diciembre por la zona horaria
  it('no corre el día por la zona horaria', () => {
    expect(fechaCorta('2026-01-01')).toBe('01/01/2026');
  });
});
