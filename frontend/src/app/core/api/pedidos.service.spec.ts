import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, Injector, runInInjectionContext, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Carrito } from '../modelos/carrito';
import { Cancelacion, Pedido } from '../modelos/pedido';
import { SesionService } from '../sesion/sesion.service';
import { CarritoService } from './carrito.service';
import { PedidosService } from './pedidos.service';

// Copia de lo que responde PedidoController (PedidoIntegracionTest)
const PEDIDO: Pedido = {
  idPedido: 12,
  estado: 'PENDIENTE_PAGO',
  lineas: [
    { idProducto: 7, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85.5, cantidad: 2, subtotal: 171 },
  ],
  total: 171,
  nombreRetira: 'Ana Paz',
  telefonoRetira: '70000000',
  nota: null,
  confirmado: '2026-10-19T10:00:00',
  vence: '2026-10-19T10:30:00',
  segundosParaPagar: 1800,
  retiro: { direccion: 'Av. Principal 123', horario: null },
  pagado: null,
  cobro: null,
};

const VACIO: Carrito = { lineas: [], total: 0, cantidadLineas: 0, idPedidoPendiente: null, correoVerificado: true };

// HU-12: llamadas a /api/pedidos
describe('PedidosService', () => {
  let http: HttpTestingController;

  async function servicio(): Promise<PedidosService> {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: SesionService, useValue: { usuario: signal(null), rol: () => 'CLIENTE' } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    // El carrito del encabezado ya cargado
    TestBed.inject(CarritoService);
    TestBed.tick();
    http.expectOne('/api/carrito').flush(VACIO);
    await estable();
    return TestBed.inject(PedidosService);
  }

  const estable = () => TestBed.inject(ApplicationRef).whenStable();

  afterEach(() => http.verify());

  // HU-12 RF-2
  it('confirmar manda los datos limpios y recarga el carrito', async () => {
    const pedidos = await servicio();

    const confirmado = pedidos.confirmar({
      nombreRetira: '  Ana Paz ',
      telefonoRetira: ' 70000000',
      nota: '   ',
      totalEsperado: 171,
    });
    const pedido = http.expectOne({ method: 'POST', url: '/api/pedidos' });
    expect(pedido.request.body).toEqual({
      nombreRetira: 'Ana Paz',
      telefonoRetira: '70000000',
      nota: null,
      totalEsperado: 171,
    });
    pedido.flush(PEDIDO, { status: 201, statusText: 'Created' });

    await expect(confirmado).resolves.toEqual(PEDIDO);
    TestBed.tick();
    http.expectOne({ method: 'GET', url: '/api/carrito' }).flush({ ...VACIO, idPedidoPendiente: 12 });
    await estable();
    expect(TestBed.inject(CarritoService).carrito.value()?.idPedidoPendiente).toBe(12);
  });

  // HU-12 RF-3: el rechazo trae cada línea con su motivo, y el carrito no se pide de nuevo
  it('si la API rechaza, falla con el código y las líneas', async () => {
    const pedidos = await servicio();

    const confirmado = pedidos.confirmar({ nombreRetira: 'Ana', telefonoRetira: '1', nota: '', totalEsperado: 171 });
    http.expectOne('/api/pedidos').flush(
      {
        status: 409,
        codigo: 'STOCK_INSUFICIENTE',
        detail: 'Algunos productos del carrito no se pueden confirmar.',
        lineas: [{ idProducto: 7, motivo: 'NO_ALCANZA' }],
      },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(confirmado).rejects.toMatchObject({
      codigo: 'STOCK_INSUFICIENTE',
      lineas: [{ idProducto: 7, motivo: 'NO_ALCANZA' }],
    });
    http.expectNone('/api/carrito');
  });

  // HU-12 RF-9
  it('cancelar usa la API y recarga el carrito', async () => {
    const pedidos = await servicio();
    const respuesta: Cancelacion = { pedido: { ...PEDIDO, estado: 'CANCELADO', segundosParaPagar: 0 }, lineasQueNoVolvieron: 0 };

    const cancelado = pedidos.cancelar(12);
    http.expectOne({ method: 'POST', url: '/api/pedidos/12/cancelacion' }).flush(respuesta);

    await expect(cancelado).resolves.toEqual(respuesta);
    TestBed.tick();
    http.expectOne({ method: 'GET', url: '/api/carrito' }).flush(VACIO);
  });

  // HU-13 RF-1, RF-4, RF-12: cada uno a su ruta, sin tocar el carrito
  it('pagar, consultar y simular llaman a su ruta', async () => {
    const pedidos = await servicio();
    const conCobro: Pedido = { ...PEDIDO, cobro: { codigoQr: 'QR', urlPasarela: null, simulador: true } };

    const pago = pedidos.pagar(12);
    http.expectOne({ method: 'POST', url: '/api/pedidos/12/cobro' }).flush(conCobro);
    await expect(pago).resolves.toEqual(conCobro);

    const consulta = pedidos.consultarPago(12);
    http.expectOne({ method: 'POST', url: '/api/pedidos/12/cobro/consulta' }).flush(conCobro);
    await expect(consulta).resolves.toEqual(conCobro);

    const simulado = pedidos.simular(12, 'PAGADO');
    const simulacion = http.expectOne({ method: 'POST', url: '/api/pedidos/12/cobro/simulacion' });
    expect(simulacion.request.body).toEqual({ resultado: 'PAGADO' });
    simulacion.flush(null, { status: 204, statusText: 'No Content' });
    await expect(simulado).resolves.toBeUndefined();
    TestBed.tick();
    http.expectNone('/api/carrito');
  });

  // HU-14 RF-1
  it('misPedidos pide la página', async () => {
    const pedidos = await servicio();
    const pagina = signal(0);
    const lista = runInInjectionContext(TestBed.inject(Injector), () => pedidos.misPedidos(pagina));
    TestBed.tick();
    http.expectOne({ method: 'GET', url: '/api/pedidos?pagina=0' }).flush({ pendiente: null, pagina: { contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 } });
    await estable();
    expect(lista.value()?.pendiente).toBeNull();

    pagina.set(2);
    TestBed.tick();
    http.expectOne('/api/pedidos?pagina=2');
  });

  // HU-12 RF-8, RF-17
  it('lee el pedido solo con un id, y dónde retirar', async () => {
    const pedidos = await servicio();
    const id = signal<number | null>(null);
    const injector = TestBed.inject(Injector);
    const pedido = runInInjectionContext(injector, () => pedidos.pedido(id));
    const retiro = runInInjectionContext(injector, () => pedidos.retiro());
    TestBed.tick();
    http.expectNone((pedido) => pedido.url.startsWith('/api/pedidos/1'));

    http.expectOne({ method: 'GET', url: '/api/pedidos/retiro' }).flush({ direccion: null, horario: null });
    id.set(12);
    TestBed.tick();
    http.expectOne({ method: 'GET', url: '/api/pedidos/12' }).flush(PEDIDO);
    await estable();

    expect(pedido.value()).toEqual(PEDIDO);
    expect(retiro.value()).toEqual({ direccion: null, horario: null });
  });
});
