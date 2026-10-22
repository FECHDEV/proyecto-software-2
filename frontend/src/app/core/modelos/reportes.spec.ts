import { FiltrosDeReportes, hoyEnBolivia, leerConsultaDeReportes, periodoDeAtajo, consultaDeReportes } from './reportes';

const HOY = '2026-10-22';

function leer(consulta: Record<string, string>, hoy = HOY) {
  return leerConsultaDeReportes((clave) => consulta[clave] ?? null, hoy);
}

const DE_ENTRADA: FiltrosDeReportes = {
  seccion: 'resumen',
  desde: '2026-10-01',
  hasta: HOY,
  tipo: null,
  orden: 'MAS',
  dias: 30,
  pagina: 0,
};

// HU-18: la dirección de /reportes
describe('consulta de reportes', () => {
  // HU-18 RF-2
  it('sin nada es este mes', () => {
    expect(leer({})).toEqual({ filtros: DE_ENTRADA, ajustado: false });
    expect(consultaDeReportes(DE_ENTRADA, HOY)).toEqual({});
  });

  // HU-18 RF-2, RF-5: lo que filtra queda en la dirección; lo de entrada, no
  it('lee y escribe lo que filtra', () => {
    const filtros: FiltrosDeReportes = {
      seccion: 'productos',
      desde: '2026-09-01',
      hasta: '2026-09-30',
      tipo: 'TALLER',
      orden: 'MENOS',
      dias: 60,
      pagina: 2,
    };
    const consulta = consultaDeReportes(filtros, HOY);

    expect(consulta).toEqual({
      seccion: 'productos',
      desde: '2026-09-01',
      hasta: '2026-09-30',
      tipo: 'TALLER',
      orden: 'MENOS',
      dias: 60,
      pagina: 2,
    });
    expect(leer(Object.fromEntries(Object.entries(consulta).map(([clave, valor]) => [clave, String(valor)])))).toEqual({
      filtros,
      ajustado: false,
    });
  });

  // HU-18 RF-3: lo que no sirve se lee como lo más cercano; si era el período, avisa
  it('corrige lo que no sirve y avisa', () => {
    expect(leer({ seccion: 'zz', tipo: 'MAYORISTA', orden: 'x', dias: '7', pagina: '-1' })).toEqual({
      filtros: DE_ENTRADA,
      ajustado: false,
    });
    expect(leer({ desde: 'xx', hasta: '2026-10-05' })).toEqual({ filtros: DE_ENTRADA, ajustado: true });
    expect(leer({ desde: '2026-02-30', hasta: '2026-03-05' }).ajustado).toBe(true);
    expect(leer({ desde: '2026-10-01' })).toEqual({ filtros: DE_ENTRADA, ajustado: true });
    // Hasta futura: hoy
    expect(leer({ desde: '2026-10-10', hasta: '2026-12-31' })).toEqual({
      filtros: { ...DE_ENTRADA, desde: '2026-10-10' },
      ajustado: true,
    });
    // Al revés: se intercambian
    expect(leer({ desde: '2026-10-10', hasta: '2026-10-01' }).filtros).toMatchObject({
      desde: '2026-10-01',
      hasta: '2026-10-10',
    });
    // Más de 366 días: desde se acerca a hasta
    expect(leer({ desde: '2024-01-01', hasta: '2026-10-22' })).toEqual({
      filtros: { ...DE_ENTRADA, desde: '2025-10-22' },
      ajustado: true,
    });
    expect(leer({ desde: '2025-10-22', hasta: '2026-10-22' }).ajustado).toBe(false);
  });

  // HU-18 RF-3: hoy es el de Bolivia, no el del navegador
  it('hoy es el de Bolivia', () => {
    // 02:00 del 23 en UTC son las 22:00 del 22 en La Paz
    expect(hoyEnBolivia(new Date('2026-10-23T02:00:00Z'))).toBe('2026-10-22');
    expect(hoyEnBolivia(new Date('2026-10-23T04:00:00Z'))).toBe('2026-10-23');
  });

  // HU-18 RF-2
  it('los atajos del período', () => {
    expect(periodoDeAtajo('hoy', HOY)).toEqual({ desde: HOY, hasta: HOY });
    expect(periodoDeAtajo('semana', HOY)).toEqual({ desde: '2026-10-16', hasta: HOY });
    expect(periodoDeAtajo('mes', HOY)).toEqual({ desde: '2026-10-01', hasta: HOY });
    expect(periodoDeAtajo('mes-anterior', HOY)).toEqual({ desde: '2026-09-01', hasta: '2026-09-30' });
    expect(periodoDeAtajo('mes-anterior', '2026-03-10')).toEqual({ desde: '2026-02-01', hasta: '2026-02-28' });
  });
});
