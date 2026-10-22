import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ReportesService } from '../../core/api/reportes.service';
import { FiltrosDeReportes } from '../../core/modelos/reportes';
import { pagina, ReportesFalso } from './reportes-falso.spec-helper';
import { ReportesClientes } from './reportes-clientes';
import { ReportesPerdidos } from './reportes-perdidos';
import { ReportesProductos } from './reportes-productos';
import { ReportesSinVenta } from './reportes-sin-venta';

const FILTROS: FiltrosDeReportes = {
  seccion: 'productos',
  desde: '2026-10-01',
  hasta: '2026-10-22',
  tipo: null,
  orden: 'MAS',
  dias: 30,
  pagina: 0,
};

// HU-18: las listas de los reportes (productos, sin venderse, clientes y perdidos)
describe('Listas de los reportes', () => {
  let reportes: ReportesFalso;
  let fixture: ComponentFixture<unknown>;
  let cambios: Partial<FiltrosDeReportes>[];

  async function abrir<T>(
    componente: Type<T>,
    preparar: (falso: ReportesFalso) => void,
    filtros: Partial<FiltrosDeReportes> = {},
  ) {
    reportes = new ReportesFalso();
    preparar(reportes);
    cambios = [];
    TestBed.configureTestingModule({ providers: [{ provide: ReportesService, useValue: reportes }] });
    const creado = TestBed.createComponent(componente);
    creado.componentRef.setInput('filtros', { ...FILTROS, ...filtros });
    (creado.componentInstance as { cambiar: { subscribe(fn: (c: Partial<FiltrosDeReportes>) => void): void } }).cambiar.subscribe(
      (cambio) => cambios.push(cambio),
    );
    fixture = creado as ComponentFixture<unknown>;
    fixture.autoDetectChanges();
    await fixture.whenStable();
  }

  const pantalla = () => fixture.nativeElement as HTMLElement;
  const texto = () => (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  const filas = () =>
    Array.from(pantalla().querySelectorAll('tbody tr')).map((fila) =>
      Array.from(fila.children).map((celda) => (celda.textContent ?? '').replace(/\s+/g, ' ').trim()),
    );
  const boton = (nombre: string) =>
    Array.from(pantalla().querySelectorAll('button')).find((cada) => cada.textContent?.trim() === nombre)!;

  // HU-18 RF-11, RF-12: más y menos vendidos, con la unidad, el inactivo marcado y el margen
  it('productos más y menos vendidos', async () => {
    await abrir(ReportesProductos, (falso) =>
      falso.productosRecurso.value.set(
        pagina(
          [
            { idProducto: 1, codigo: 'HER-0001', nombre: 'Taladro', unidad: 'UNIDAD', activo: false, cantidad: 1, importe: 100, margen: -5, sinCosto: 0 },
            { idProducto: 2, codigo: 'ELE-0002', nombre: 'Cable', unidad: 'METRO', activo: true, cantidad: 2.5, importe: 30, margen: null, sinCosto: 30 },
          ],
          0,
          3,
        ),
      ),
    );

    expect(filas()[0][0]).toContain('Taladro');
    expect(filas()[0][0]).toContain('Inactivo');
    expect(filas()[0][0]).toContain('HER-0001');
    expect(filas()[1][1]).toBe('2,5 · Metro');
    expect(filas()[1][3]).toBe('Sin costo');
    expect(pantalla().querySelector('td.negativo')?.textContent).toContain('5,00');
    expect(boton('Más vendidos').getAttribute('aria-pressed')).toBe('true');

    boton('Menos vendidos').click();
    boton('Siguiente').click();
    expect(cambios).toEqual([{ orden: 'MENOS' }, { pagina: 1 }]);
    expect(texto()).toContain('Página 1 de 3');
  });

  // HU-18 RF-13: los días, la última venta (o «Nunca») y el inmovilizado a precio marcado
  it('sin venderse', async () => {
    await abrir(
      ReportesSinVenta,
      (falso) =>
        falso.sinVentaRecurso.value.set(
          pagina([
            { idProducto: 3, codigo: 'JAR-0003', nombre: 'Rastrillo', unidad: 'UNIDAD', stock: 3, precio: 20, costo: null, ultimaVenta: null, diasSinVenta: 100, inmovilizado: 60, inmovilizadoAPrecio: true },
            { idProducto: 4, codigo: 'JAR-0004', nombre: 'Carretilla', unidad: 'UNIDAD', stock: 4, precio: 450, costo: 300, ultimaVenta: '2026-08-08T10:00:00', diasSinVenta: 75, inmovilizado: 1200, inmovilizadoAPrecio: false },
          ]),
        ),
      { seccion: 'sin-venta', dias: 60 },
    );

    expect(filas()[0].slice(1, 3)).toEqual(['100', 'Nunca']);
    expect(filas()[0][6]).toContain('a precio');
    expect(filas()[1].slice(1, 3)).toEqual(['75', '08/08/2026']);
    expect(filas()[1][6]).toContain('1.200,00');
    expect(filas()[1][6]).not.toContain('a precio');
    expect(boton('Más de 60 días').getAttribute('aria-pressed')).toBe('true');

    boton('Más de 90 días').click();
    expect(cambios).toEqual([{ dias: 90 }]);
  });

  // HU-18 RF-16: con el contacto para llamarlos
  it('clientes inactivos', async () => {
    await abrir(
      ReportesClientes,
      (falso) =>
        falso.clientesRecurso.value.set(
          pagina([
            { idUsuario: 9, nombre: 'Ana', apellido: 'Paz', correo: 'ana@mail.com', telefono: '70000000', tipo: 'CONTRATISTA', ultimaCompra: '2026-09-07T10:00:00', pedidos: 2, total: 150 },
          ]),
        ),
      { seccion: 'clientes' },
    );

    expect(filas()[0][0]).toBe('Ana Paz Contratista');
    expect(pantalla().querySelector('a[href="mailto:ana@mail.com"]')).not.toBeNull();
    expect(pantalla().querySelector('a[href="tel:70000000"]')).not.toBeNull();
    expect(filas()[0].slice(2, 4)).toEqual(['07/09/2026', '2']);
    expect(filas()[0][4]).toContain('150,00');
  });

  // HU-18 RF-17, RF-18: las cifras y los productos de los pedidos perdidos
  it('pedidos perdidos', async () => {
    await abrir(
      ReportesPerdidos,
      (falso) => {
        falso.resumenDePerdidosRecurso.value.set({ cantidad: 3, importe: 350, confirmados: 10, vendidos: 7, proporcion: 70 });
        falso.perdidosRecurso.value.set(
          pagina([{ idProducto: 1, codigo: null, nombre: 'Taladro', unidad: 'UNIDAD', cantidad: 1, importe: 100 }]),
        );
      },
      { seccion: 'perdidos' },
    );

    expect(texto()).toContain('Pedidos perdidos3');
    expect(texto()).toContain('350,00');
    expect(texto()).toContain('70 %');
    expect(texto()).toContain('7 de 10 confirmados');
    expect(filas()[0][0]).toBe('Taladro');
    expect(filas()[0][2]).toContain('100,00');
    expect(reportes.pedidas).toEqual(['resumen-de-perdidos', 'perdidos']);
  });

  // /code-review, RF-22: si fallan las cifras de los perdidos, también se avisa y se reintentan las dos
  it('un error de las cifras de perdidos se avisa', async () => {
    await abrir(
      ReportesPerdidos,
      (falso) => {
        falso.resumenDePerdidosRecurso.error.set({ status: 0 });
        falso.perdidosRecurso.value.set(pagina([]));
      },
      { seccion: 'perdidos' },
    );

    boton('Reintentar').click();

    expect(reportes.resumenDePerdidosRecurso.reload).toHaveBeenCalled();
    // La lista ya estaba (/code-review, ronda 2)
    expect(reportes.perdidosRecurso.reload).not.toHaveBeenCalled();
    expect(texto()).not.toContain('Importe perdido');
  });

  // HU-18 RF-22: cada lista dice cuando no hay nada, en vez de una tabla vacía
  it('sin datos lo dice cada lista', async () => {
    await abrir(ReportesProductos, (falso) => falso.productosRecurso.value.set(pagina([])));
    expect(texto()).toContain('No hubo ventas en este período.');
    expect(pantalla().querySelector('table')).toBeNull();
    TestBed.resetTestingModule();

    await abrir(ReportesSinVenta, (falso) => falso.sinVentaRecurso.value.set(pagina([])), { dias: 90 });
    expect(texto()).toContain('Ningún producto lleva más de 90 días sin venderse.');
    TestBed.resetTestingModule();

    await abrir(ReportesClientes, (falso) => falso.clientesRecurso.value.set(pagina([])));
    expect(texto()).toContain('Ningún cliente dejó de comprar hace más de 30 días.');
    TestBed.resetTestingModule();

    await abrir(ReportesPerdidos, (falso) => falso.perdidosRecurso.value.set(pagina([])));
    expect(texto()).toContain('No hubo pedidos perdidos en este período.');
  });

  // HU-18 RF-22: un error deja reintentar
  it('un error de carga deja reintentar', async () => {
    await abrir(ReportesClientes, (falso) => falso.clientesRecurso.error.set({ status: 0 }));

    boton('Reintentar').click();

    expect(reportes.clientesRecurso.reload).toHaveBeenCalled();
  });
});
