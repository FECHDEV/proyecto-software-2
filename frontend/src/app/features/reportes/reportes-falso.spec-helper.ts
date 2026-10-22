import { signal, Signal } from '@angular/core';
import { vi } from 'vitest';

import { Pagina } from '../../core/modelos/pagina';
import {
  ClienteInactivo,
  FiltrosDeReportes,
  ProductoPerdido,
  ProductoSinVenta,
  ProductoVendido,
  ResumenDePerdidos,
  VentasDelPeriodo,
} from '../../core/modelos/reportes';

// Lo que las pantallas usan de un httpResource
export class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly error = signal<unknown>(undefined);
  readonly isLoading = signal(false);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

// El ReportesService de las pruebas: guarda los filtros con que se pidió cada lectura
export class ReportesFalso {
  readonly ventasRecurso = new RecursoFalso<VentasDelPeriodo>();
  readonly productosRecurso = new RecursoFalso<Pagina<ProductoVendido>>();
  readonly sinVentaRecurso = new RecursoFalso<Pagina<ProductoSinVenta>>();
  readonly clientesRecurso = new RecursoFalso<Pagina<ClienteInactivo>>();
  readonly perdidosRecurso = new RecursoFalso<Pagina<ProductoPerdido>>();
  readonly resumenDePerdidosRecurso = new RecursoFalso<ResumenDePerdidos>();
  filtros: Signal<FiltrosDeReportes> | null = null;
  pedidas: string[] = [];

  ventas(filtros: Signal<FiltrosDeReportes>) {
    return this.pedir('ventas', filtros, this.ventasRecurso);
  }
  productos(filtros: Signal<FiltrosDeReportes>) {
    return this.pedir('productos', filtros, this.productosRecurso);
  }
  sinVenta(filtros: Signal<FiltrosDeReportes>) {
    return this.pedir('sin-venta', filtros, this.sinVentaRecurso);
  }
  clientesInactivos(filtros: Signal<FiltrosDeReportes>) {
    return this.pedir('clientes', filtros, this.clientesRecurso);
  }
  resumenDePerdidos(filtros: Signal<FiltrosDeReportes>) {
    return this.pedir('resumen-de-perdidos', filtros, this.resumenDePerdidosRecurso);
  }
  perdidos(filtros: Signal<FiltrosDeReportes>) {
    return this.pedir('perdidos', filtros, this.perdidosRecurso);
  }

  private pedir<T>(nombre: string, filtros: Signal<FiltrosDeReportes>, recurso: T): T {
    this.pedidas.push(nombre);
    this.filtros = filtros;
    return recurso;
  }
}

export function pagina<T>(contenido: T[], numero = 0, totalPaginas = 1): Pagina<T> {
  return { contenido, pagina: numero, tamano: 10, totalElementos: contenido.length, totalPaginas };
}

const TOTALES_EN_CERO = {
  ingresos: 0,
  pedidos: 0,
  promedio: null,
  margen: null,
  margenPorcentaje: null,
  vendidoSinCosto: 0,
  devolverCantidad: 0,
  devolverImporte: 0,
};

// Como responde GET /api/reportes/ventas (ReportesIntegracionTest): el escenario de la historia
export function ventas(cambios: Partial<VentasDelPeriodo> = {}): VentasDelPeriodo {
  return {
    periodo: {
      desde: '2026-10-01',
      hasta: '2026-10-22',
      anteriorDesde: '2026-09-09',
      anteriorHasta: '2026-09-30',
      agrupado: 'DIA',
    },
    actual: {
      ingresos: 600,
      pedidos: 3,
      promedio: 200,
      margen: 51,
      margenPorcentaje: 29.8,
      vendidoSinCosto: 40,
      devolverCantidad: 1,
      devolverImporte: 50,
    },
    anterior: { ...TOTALES_EN_CERO, ingresos: 480, pedidos: 3, promedio: 160, margen: 60, margenPorcentaje: 30 },
    porFecha: [
      { fecha: '2026-10-01', ingresos: 0, pedidos: 0 },
      { fecha: '2026-10-02', ingresos: 600, pedidos: 3 },
    ],
    porDiaSemana: [1, 2, 3, 4, 5, 6, 7].map((dia) => ({ dia, ingresos: dia === 5 ? 600 : 0, pedidos: dia === 5 ? 3 : 0 })),
    porHora: Array.from({ length: 24 }, (_, hora) => ({ hora, ingresos: hora === 8 ? 600 : 0, pedidos: hora === 8 ? 3 : 0 })),
    porCategoria: [
      { categoria: 'Herramientas', ingresos: 500, pedidos: 2, margen: -15 },
      { categoria: 'Jardín', ingresos: 100, pedidos: 1, margen: null },
    ],
    porTipo: [
      { tipo: 'PUBLICO_GENERAL', ingresos: 400, pedidos: 2, promedio: 200 },
      { tipo: 'CONTRATISTA', ingresos: 200, pedidos: 1, promedio: 200 },
      { tipo: 'TALLER', ingresos: 0, pedidos: 0, promedio: null },
      { tipo: 'EMPRESA', ingresos: 0, pedidos: 0, promedio: null },
    ],
    ...cambios,
  };
}

export const SIN_VENTAS: Partial<VentasDelPeriodo> = {
  actual: TOTALES_EN_CERO,
  anterior: TOTALES_EN_CERO,
  porTipo: (['PUBLICO_GENERAL', 'CONTRATISTA', 'TALLER', 'EMPRESA'] as const).map((tipo) => ({
    tipo,
    ingresos: 0,
    pedidos: 0,
    promedio: null,
  })),
};
