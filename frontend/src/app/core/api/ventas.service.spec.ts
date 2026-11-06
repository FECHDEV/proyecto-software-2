import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { SolicitudDeVenta, Venta } from '../modelos/venta';
import { VentasService } from './ventas.service';

// Copia de lo que responde VentaTiendaController (VentaTiendaIntegracionTest)
const VENTA: Venta = {
  idPedido: 31,
  estado: 'ENTREGADO',
  confirmado: '2026-11-05T10:00:00',
  vence: '2026-11-05T10:10:00',
  segundosParaPagar: 0,
  pagado: '2026-11-05T10:00:00',
  comprador: null,
  vendidoPor: 'Eva Paz',
  lineas: [{ idProducto: 1, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85, cantidad: 1, subtotal: 85 }],
  total: 85,
  medio: 'EFECTIVO',
  recibido: 100,
  cambio: 15,
  cobro: null,
  anulado: null,
  anuladoPor: null,
  motivoAnulacion: null,
  anulable: true,
};

const SOLICITUD: SolicitudDeVenta = {
  lineas: [{ idProducto: 1, cantidad: 1 }],
  idCliente: null,
  medio: 'EFECTIVO',
  recibido: 100,
  totalEsperado: 85,
};

// HU-23: llamadas a /api/ventas
describe('VentasService', () => {
  let servicio: VentasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(VentasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-23 RF-1: sin texto no pide nada; con texto, recortado
  it('busca productos solo con texto', () => {
    const texto = signal('  ');
    TestBed.runInInjectionContext(() => servicio.productos(texto));
    TestBed.tick();
    http.expectNone(() => true);

    texto.set(' martillo ');
    TestBed.tick();
    const pedido = http.expectOne((cada) => cada.url === '/api/ventas/productos');
    expect(pedido.request.params.get('texto')).toBe('martillo');
    pedido.flush([]);
  });

  // HU-23 RF-15: el comprobante, y nada sin id
  it('lee la venta, y nada sin id', () => {
    const id = signal<number | null>(null);
    TestBed.runInInjectionContext(() => servicio.venta(id));
    TestBed.tick();
    http.expectNone(() => true);

    id.set(31);
    TestBed.tick();
    http.expectOne('/api/ventas/31').flush(VENTA);
  });

  // HU-23 RF-4: el correo recortado; si no es un Cliente activo, el 404 con su código
  it('busca al comprador por correo', async () => {
    const encontrado = servicio.buscarCliente(' ana@mail.com ');
    const pedido = http.expectOne((cada) => cada.url === '/api/ventas/clientes');
    expect(pedido.request.params.get('correo')).toBe('ana@mail.com');
    pedido.flush({ idUsuario: 4, nombre: 'Ana Paz', correo: 'ana@mail.com' });
    expect(await encontrado).toEqual({ idUsuario: 4, nombre: 'Ana Paz', correo: 'ana@mail.com' });

    const ninguno = servicio.buscarCliente('x@mail.com');
    http
      .expectOne((cada) => cada.url === '/api/ventas/clientes')
      .flush({ status: 404, detail: 'x', codigo: 'USUARIO_NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
    await expect(ninguno).rejects.toMatchObject({ status: 404, codigo: 'USUARIO_NO_ENCONTRADO' });
  });

  // HU-23 RF-5, RF-6: registra con lo que mandó la pantalla
  it('registra la venta', async () => {
    const respuesta = servicio.registrar(SOLICITUD);
    const pedido = http.expectOne('/api/ventas');
    expect(pedido.request.method).toBe('POST');
    expect(pedido.request.body).toEqual(SOLICITUD);
    pedido.flush(VENTA);

    expect(await respuesta).toEqual(VENTA);
  });

  // HU-23 RF-12: el rechazo llega con su código y las líneas
  it('un rechazo trae el código y las líneas', async () => {
    const respuesta = servicio.registrar(SOLICITUD);
    http.expectOne('/api/ventas').flush(
      { status: 409, detail: 'x', codigo: 'STOCK_INSUFICIENTE', lineas: [{ idProducto: 1, motivo: 'NO_ALCANZA', disponible: 0 }] },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(respuesta).rejects.toMatchObject({ status: 409, codigo: 'STOCK_INSUFICIENTE' });
  });

  // HU-23 RF-8, RF-9, RF-18, RF-19 y el simulador
  it('consulta, cancela, anula con el motivo recortado y simula', async () => {
    const consulta = servicio.consultar(31);
    http.expectOne('/api/ventas/31/consulta').flush(VENTA);
    await consulta;

    const cancelacion = servicio.cancelar(31);
    http.expectOne('/api/ventas/31/cancelacion').flush(VENTA);
    await cancelacion;

    const anulacion = servicio.anular(31, '  Producto equivocado  ');
    const pedido = http.expectOne('/api/ventas/31/anulacion');
    expect(pedido.request.body).toEqual({ motivo: 'Producto equivocado' });
    pedido.flush(VENTA);
    await anulacion;

    const simulacion = servicio.simular(31, 'PAGADO');
    const simulado = http.expectOne('/api/ventas/31/simulacion');
    expect(simulado.request.body).toEqual({ resultado: 'PAGADO' });
    simulado.flush(null);
    await simulacion;
  });
});
