import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { FiltrosDeReportes } from '../modelos/reportes';
import { ReportesService } from './reportes.service';

const PAGINA = { contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 };
const DE_ENTRADA: FiltrosDeReportes = {
  seccion: 'resumen',
  desde: '2026-10-01',
  hasta: '2026-10-22',
  tipo: null,
  orden: 'MAS',
  dias: 30,
  pagina: 0,
};

// HU-18: llamadas a /api/reportes
describe('ReportesService', () => {
  let servicio: ReportesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ReportesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function pedido(url: string) {
    return http.expectOne((cada) => cada.url === url);
  }

  // HU-18 RF-2, RF-5: el período siempre; el tipo, solo si filtra
  it('pide las ventas solo con lo que filtra', () => {
    TestBed.runInInjectionContext(() => servicio.ventas(signal(DE_ENTRADA)));
    TestBed.tick();
    const deEntrada = pedido('/api/reportes/ventas');
    expect(deEntrada.request.params.keys()).toEqual(['desde', 'hasta']);
    expect(deEntrada.request.params.get('desde')).toBe('2026-10-01');
    deEntrada.flush({});

    TestBed.runInInjectionContext(() => servicio.ventas(signal({ ...DE_ENTRADA, tipo: 'EMPRESA', pagina: 3 })));
    TestBed.tick();
    const conTipo = pedido('/api/reportes/ventas');
    expect(conTipo.request.params.get('tipo')).toBe('EMPRESA');
    expect(conTipo.request.params.has('pagina')).toBe(false);
    conTipo.flush({});
  });

  // HU-18 RF-11 a RF-13, RF-16, RF-18: cada lista con lo suyo y su página
  it('pide cada lista con su página', () => {
    const filtros = { ...DE_ENTRADA, tipo: 'TALLER' as const, orden: 'MENOS' as const, dias: 60 as const, pagina: 2 };
    TestBed.runInInjectionContext(() => {
      servicio.productos(signal(filtros));
      servicio.sinVenta(signal(filtros));
      servicio.clientesInactivos(signal(filtros));
      servicio.perdidos(signal(filtros));
      servicio.resumenDePerdidos(signal(filtros));
    });
    TestBed.tick();

    const productos = pedido('/api/reportes/productos');
    expect(productos.request.params.keys().sort()).toEqual(['desde', 'hasta', 'orden', 'pagina', 'tipo']);
    expect(productos.request.params.get('orden')).toBe('MENOS');
    const sinVenta = pedido('/api/reportes/sin-venta');
    expect(sinVenta.request.params.keys().sort()).toEqual(['dias', 'pagina']);
    expect(sinVenta.request.params.get('dias')).toBe('60');
    const clientes = pedido('/api/reportes/clientes-inactivos');
    expect(clientes.request.params.keys().sort()).toEqual(['dias', 'pagina', 'tipo']);
    const perdidos = pedido('/api/reportes/perdidos');
    expect(perdidos.request.params.keys().sort()).toEqual(['desde', 'hasta', 'pagina', 'tipo']);
    expect(perdidos.request.params.get('pagina')).toBe('2');
    const resumen = pedido('/api/reportes/perdidos/resumen');
    expect(resumen.request.params.keys().sort()).toEqual(['desde', 'hasta', 'tipo']);
    [productos, sinVenta, clientes, perdidos].forEach((cada) => cada.flush(PAGINA));
    resumen.flush({});
  });

  // HU-19 RF-2: el período, el tipo y los días de la pantalla, en cualquier sección
  it('exporta con el formato, el período, el tipo y los días', async () => {
    const exportado = servicio.exportar({ ...DE_ENTRADA, seccion: 'sin-venta', tipo: 'TALLER', dias: 90, pagina: 3 }, 'pdf');

    const archivo = pedido('/api/reportes/exportar');
    expect(archivo.request.responseType).toBe('blob');
    expect(archivo.request.params.keys().sort()).toEqual(['desde', 'dias', 'formato', 'hasta', 'tipo']);
    expect(archivo.request.params.get('formato')).toBe('pdf');
    expect(archivo.request.params.get('tipo')).toBe('TALLER');
    expect(archivo.request.params.get('dias')).toBe('90');
    archivo.flush(new Blob(['%PDF-']), {
      headers: { 'Content-Disposition': 'attachment; filename="reporte-ventas-2026-10-01-a-2026-10-22.pdf"' },
    });

    // HU-19 RF-4: el nombre que puso el backend
    const { archivo: blob, nombre } = await exportado;
    expect(nombre).toBe('reporte-ventas-2026-10-01-a-2026-10-22.pdf');
    expect(await blob.text()).toBe('%PDF-');
  });

  // HU-19 RF-4: sin el encabezado, un nombre genérico
  it('nombra el archivo si el backend no lo dice', async () => {
    const exportado = servicio.exportar(DE_ENTRADA, 'xlsx');

    pedido('/api/reportes/exportar').flush(new Blob(['xlsx']));

    expect((await exportado).nombre).toBe('reporte-ventas.xlsx');
  });

  // HU-19 RF-12: el error llega como Blob y se lee como cualquier ErrorApi
  it('convierte el error en Blob en ErrorApi', async () => {
    const exportado = servicio.exportar(DE_ENTRADA, 'xlsx');
    const cuerpo = { status: 400, detail: 'mal', codigo: 'PERIODO_INVALIDO' };

    pedido('/api/reportes/exportar').flush(new Blob([JSON.stringify(cuerpo)], { type: 'application/problem+json' }), {
      status: 400,
      statusText: 'Bad Request',
    });

    await expect(exportado).rejects.toMatchObject({ status: 400, codigo: 'PERIODO_INVALIDO' });
  });

  // HU-19 RF-12: sin respuesta, como cualquier otra falta de conexión
  it('sin conexión rechaza con el error de conexión', async () => {
    const exportado = servicio.exportar(DE_ENTRADA, 'pdf');

    pedido('/api/reportes/exportar').error(new ProgressEvent('error'), { status: 0 });

    await expect(exportado).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});

