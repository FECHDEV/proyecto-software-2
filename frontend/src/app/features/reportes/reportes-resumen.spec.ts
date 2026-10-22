import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReportesService } from '../../core/api/reportes.service';
import { FiltrosDeReportes } from '../../core/modelos/reportes';
import { ReportesFalso, SIN_VENTAS, ventas } from './reportes-falso.spec-helper';
import { ReportesResumen } from './reportes-resumen';

const FILTROS: FiltrosDeReportes = {
  seccion: 'resumen',
  desde: '2026-10-01',
  hasta: '2026-10-22',
  tipo: null,
  orden: 'MAS',
  dias: 30,
  pagina: 0,
};

// HU-18: el resumen del período
describe('ReportesResumen', () => {
  let reportes: ReportesFalso;
  let fixture: ComponentFixture<ReportesResumen>;

  async function abrir(preparar: (falso: ReportesFalso) => void = () => {}) {
    reportes = new ReportesFalso();
    preparar(reportes);
    TestBed.configureTestingModule({ providers: [{ provide: ReportesService, useValue: reportes }] });
    fixture = TestBed.createComponent(ReportesResumen);
    fixture.componentRef.setInput('filtros', FILTROS);
    fixture.autoDetectChanges();
    await fixture.whenStable();
  }

  const pantalla = () => fixture.nativeElement as HTMLElement;
  const texto = () => (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  const cifra = (nombre: string) =>
    Array.from(pantalla().querySelectorAll('.cifra')).find((cada) => cada.querySelector('dt')?.textContent === nombre)!;
  const tabla = (titulo: string) =>
    Array.from(pantalla().querySelectorAll('app-tabla-de-ventas')).find(
      (cada) => cada.querySelector('h2')?.textContent === titulo,
    )!;
  const filas = (titulo: string) =>
    Array.from(tabla(titulo).querySelectorAll('tbody tr')).map((fila) =>
      Array.from(fila.children).map((celda) => (celda.textContent ?? '').replace(/\s+/g, ' ').trim()),
    );

  // HU-18 RF-6, RF-7: cada cifra con la del período anterior, en palabras
  it('cifras con la comparación', async () => {
    await abrir((falso) => falso.ventasRecurso.value.set(ventas()));

    expect(reportes.filtros!()).toEqual(FILTROS);
    expect(cifra('Ingresos').textContent).toContain('600,00');
    expect(cifra('Ingresos').textContent).toContain('480,00, subió 25 %');
    expect(cifra('Pedidos vendidos').textContent).toContain('3');
    expect(cifra('Pedidos vendidos').textContent).toContain('antes 3, igual');
    expect(cifra('Promedio por pedido').textContent).toContain('200,00');
    expect(cifra('Pagos a devolver').textContent).toContain('50,00');
    expect(cifra('Pagos a devolver').textContent).toContain('1 pedido');
    expect(cifra('Pagos a devolver').textContent).toContain('sin pagos a devolver antes');
    expect(texto()).toContain('Comparado con el 09/09/2026 al 30/09/2026');
  });

  // HU-18 RF-8: el margen con su porcentaje y lo vendido sin costo; negativo en el tono de error
  it('margen negativo y sin costo', async () => {
    await abrir((falso) => falso.ventasRecurso.value.set(ventas()));
    const margen = cifra('Margen');
    expect(margen.textContent).toContain('51,00');
    expect(margen.textContent).toContain('29,8 % de lo vendido con costo');
    expect(margen.textContent).toContain('40,00 vendidos sin costo cargado');
    expect(margen.textContent).toContain('bajó 15 %');
    expect(margen.querySelector('.valor')?.classList.contains('negativo')).toBe(false);

    reportes.ventasRecurso.value.set(
      ventas({ actual: { ...ventas().actual, margen: -15, margenPorcentaje: -50, vendidoSinCosto: 0 } }),
    );
    await fixture.whenStable();
    expect(cifra('Margen').querySelector('.valor')?.classList.contains('negativo')).toBe(true);
    expect(cifra('Margen').textContent).not.toContain('sin costo cargado');

    reportes.ventasRecurso.value.set(ventas({ actual: { ...ventas().actual, margen: null, margenPorcentaje: null } }));
    await fixture.whenStable();
    expect(cifra('Margen').textContent).toContain('Sin costo cargado');

    // Revisión de interfaz: sin margen antes no dice «sin ventas antes»
    reportes.ventasRecurso.value.set(ventas({ anterior: { ...ventas().anterior, margen: null } }));
    await fixture.whenStable();
    expect(cifra('Margen').textContent).toContain('sin margen antes');

    // /code-review, ronda 2: un margen de Bs 0 antes es un dato
    reportes.ventasRecurso.value.set(ventas({ anterior: { ...ventas().anterior, margen: 0 } }));
    await fixture.whenStable();
    expect(cifra('Margen').textContent).toContain('antes');
    expect(cifra('Margen').textContent).toContain('0,00');
    expect(cifra('Margen').textContent).not.toContain('sin margen antes');
  });

  // HU-18 RF-9: por día con todos los días; por mes con el nombre del mes
  it('por día y por mes', async () => {
    await abrir((falso) => falso.ventasRecurso.value.set(ventas()));
    expect(filas('Ventas por día').map((fila) => [fila[0], fila[1]])).toEqual([
      ['01/10/2026', '0'],
      ['02/10/2026', '3'],
    ]);
    expect(filas('Ventas por día')[1][2]).toContain('600,00');
    const barras = Array.from(tabla('Ventas por día').querySelectorAll<HTMLElement>('.barra'));
    expect(barras.map((barra) => barra.style.width)).toEqual(['0%', '100%']);

    reportes.ventasRecurso.value.set(
      ventas({
        periodo: { ...ventas().periodo, agrupado: 'MES' },
        porFecha: [{ fecha: '2025-10', ingresos: 600, pedidos: 3 }],
      }),
    );
    await fixture.whenStable();
    expect(filas('Ventas por mes')[0][0]).toBe('octubre de 2025');
  });

  // HU-18 RF-10
  it('semana y hora', async () => {
    await abrir((falso) => falso.ventasRecurso.value.set(ventas()));

    expect(filas('Por día de la semana').map((fila) => fila[0])).toEqual([
      'Lunes',
      'Martes',
      'Miércoles',
      'Jueves',
      'Viernes',
      'Sábado',
      'Domingo',
    ]);
    expect(filas('Por día de la semana')[4][1]).toBe('3');
    expect(filas('Por hora del pago')).toHaveLength(24);
    expect(filas('Por hora del pago')[8][0]).toBe('08:00');
  });

  // Revisión de interfaz: los conteos grandes con separador de miles
  it('los conteos llevan separador de miles', async () => {
    await abrir((falso) =>
      falso.ventasRecurso.value.set(
        ventas({
          actual: { ...ventas().actual, pedidos: 99999 },
          porFecha: [{ fecha: '2026-10-01', ingresos: 600, pedidos: 12345 }],
        }),
      ),
    );

    expect(cifra('Pedidos vendidos').querySelector('.valor')?.textContent).toBe('99.999');
    expect(filas('Ventas por día')[0][1]).toBe('12.345');
  });

  // HU-18 RF-14, RF-15: el margen por categoría (o «Sin costo») y el promedio por tipo
  it('categoría y tipo', async () => {
    await abrir((falso) => falso.ventasRecurso.value.set(ventas()));

    expect(filas('Por categoría').map((fila) => [fila[0], fila[3]])).toEqual([
      ['Herramientas', expect.stringContaining('15,00')],
      ['Jardín', 'Sin costo'],
    ]);
    expect(tabla('Por categoría').querySelector('td.negativo')?.textContent).toContain('15,00');
    expect(filas('Por tipo de cliente').map((fila) => fila[0])).toEqual([
      'Público general',
      'Contratista',
      'Taller',
      'Empresa',
    ]);
    expect(filas('Por tipo de cliente')[2][3]).toBe('—');
  });

  // HU-18 RF-22
  it('cargando, error con reintentar y sin ventas', async () => {
    await abrir();
    expect(texto()).toContain('Cargando el resumen…');

    reportes.ventasRecurso.error.set({ status: 0 });
    await fixture.whenStable();
    const reintentar = Array.from(pantalla().querySelectorAll('button')).find(
      (boton) => boton.textContent?.trim() === 'Reintentar',
    )!;
    reintentar.click();
    expect(reportes.ventasRecurso.reload).toHaveBeenCalled();

    reportes.ventasRecurso.error.set(undefined);
    reportes.ventasRecurso.value.set(ventas(SIN_VENTAS));
    await fixture.whenStable();
    expect(texto()).toContain('No hubo ventas en este período.');
    expect(pantalla().querySelector('app-tabla-de-ventas')).toBeNull();
    expect(cifra('Promedio por pedido').textContent).toContain('—');
  });

  // /code-review: «Por tipo de cliente» no se filtra (RF-15); se ve aunque el tipo elegido no vendiera
  it('sin ventas del tipo elegido sigue mostrando los cuatro tipos', async () => {
    await abrir((falso) => falso.ventasRecurso.value.set(ventas({ ...SIN_VENTAS, porTipo: ventas().porTipo })));

    expect(texto()).toContain('No hubo ventas en este período.');
    expect(filas('Por tipo de cliente').map((fila) => fila[0])).toHaveLength(4);

    // /code-review, ronda 2: con un tipo filtrado, dice de quién no hubo ventas
    fixture.componentRef.setInput('filtros', { ...FILTROS, tipo: 'CONTRATISTA' });
    await fixture.whenStable();
    expect(texto()).toContain('No hubo ventas a clientes «Contratista» en este período.');
    expect(pantalla().querySelectorAll('app-tabla-de-ventas')).toHaveLength(1);
  });
});
