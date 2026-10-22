import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { FichaDePedido, FiltrosDeDespacho } from '../modelos/despacho';
import { DespachoService } from './despacho.service';

// Copia de lo que responde DespachoController (DespachoIntegracionTest)
const FICHA: FichaDePedido = {
  idPedido: 12,
  estado: 'ENTREGADO',
  lineas: [{ idProducto: 7, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85.5, cantidad: 2, subtotal: 171 }],
  total: 171,
  nombreRetira: 'José Pérez',
  telefonoRetira: '70000000',
  nota: null,
  confirmado: '2026-10-21T10:00:00',
  pagado: '2026-10-21T10:05:00',
  entregado: '2026-10-21T16:40:00',
  entregadoPor: 'Eva Paz',
  clienteNombre: 'Ana Paz',
  clienteCorreo: 'ana@mail.com',
};

const PAGINA = { contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 };
const DE_ENTRADA: FiltrosDeDespacho = { estado: 'PAGADO', texto: '', pagina: 0 };

// HU-17: llamadas a /api/despacho/pedidos
describe('DespachoService', () => {
  let servicio: DespachoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(DespachoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-17 RF-1 a RF-3: «Pagado» es lo de entrada y no viaja; lo demás, solo si filtra
  it('pide la lista solo con los filtros que filtran', () => {
    TestBed.runInInjectionContext(() => servicio.lista(signal(DE_ENTRADA)));
    TestBed.tick();
    const deEntrada = http.expectOne((pedido) => pedido.url === '/api/despacho/pedidos');
    expect(deEntrada.request.params.keys()).toEqual([]);
    deEntrada.flush(PAGINA);

    TestBed.runInInjectionContext(() => servicio.lista(signal({ estado: 'TODOS', texto: ' josé ', pagina: 2 })));
    TestBed.tick();
    const conFiltros = http.expectOne((pedido) => pedido.url === '/api/despacho/pedidos');
    expect(conFiltros.request.params.get('estado')).toBe('TODOS');
    expect(conFiltros.request.params.get('texto')).toBe('josé');
    expect(conFiltros.request.params.get('pagina')).toBe('2');
    conFiltros.flush(PAGINA);
  });

  // HU-17 RF-4
  it('lee la ficha, y nada sin id', () => {
    const id = signal<number | null>(null);
    TestBed.runInInjectionContext(() => servicio.ficha(id));
    TestBed.tick();
    http.expectNone(() => true);

    id.set(12);
    TestBed.tick();
    http.expectOne('/api/despacho/pedidos/12').flush(FICHA);
  });

  // HU-17 RF-6
  it('registra la entrega y devuelve la ficha', async () => {
    const respuesta = servicio.entregar(12);
    const pedido = http.expectOne('/api/despacho/pedidos/12/entrega');
    expect(pedido.request.method).toBe('POST');
    pedido.flush(FICHA);

    expect(await respuesta).toEqual(FICHA);
  });

  // HU-17 RF-7: el rechazo llega con su código
  it('un rechazo de la entrega trae el código', async () => {
    const respuesta = servicio.entregar(12);
    http
      .expectOne('/api/despacho/pedidos/12/entrega')
      .flush({ status: 409, detail: 'x', codigo: 'PEDIDO_NO_ENTREGABLE' }, { status: 409, statusText: 'Conflict' });

    await expect(respuesta).rejects.toMatchObject({ status: 409, codigo: 'PEDIDO_NO_ENTREGABLE' });
  });
});
