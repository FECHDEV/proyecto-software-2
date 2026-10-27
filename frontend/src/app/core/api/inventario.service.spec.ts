import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { FiltrosDeInventario, ProductoEnInventario } from '../modelos/inventario';
import { InventarioService } from './inventario.service';

// Copia de lo que responde InventarioController (InventarioIntegracionTest)
const TALADRO: ProductoEnInventario = {
  idProducto: 7,
  codigo: 'HER-0007',
  nombre: 'Taladro',
  categoria: 'Herramientas',
  unidad: 'UNIDAD',
  activo: true,
  stock: 10,
  stockMinimo: 5,
  stockBajo: false,
  comprometido: 0,
};

const PAGINA = { contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 };
const SIN_FILTROS: FiltrosDeInventario = { texto: '', categoria: null, activo: null, soloBajo: false, pagina: 0 };

// HU-08: llamadas a /api/inventario
describe('InventarioService', () => {
  let servicio: InventarioService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(InventarioService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-08 RF-10
  it('pide la lista solo con los filtros que filtran', () => {
    TestBed.runInInjectionContext(() => servicio.listado(signal(SIN_FILTROS)));
    TestBed.tick();
    const sinFiltros = http.expectOne((pedido) => pedido.url === '/api/inventario');
    expect(sinFiltros.request.params.keys()).toEqual([]);
    sinFiltros.flush(PAGINA);

    TestBed.runInInjectionContext(() =>
      servicio.listado(signal({ texto: ' tala ', categoria: 4, activo: false, soloBajo: true, pagina: 2 })),
    );
    TestBed.tick();
    const conFiltros = http.expectOne((pedido) => pedido.url === '/api/inventario');
    expect(conFiltros.request.params.get('texto')).toBe('tala');
    expect(conFiltros.request.params.get('categoria')).toBe('4');
    expect(conFiltros.request.params.get('activo')).toBe('false');
    expect(conFiltros.request.params.get('bajo')).toBe('true');
    expect(conFiltros.request.params.get('pagina')).toBe('2');
    conFiltros.flush(PAGINA);
  });

  // HU-08 RF-11
  it('lee el producto y su historial por páginas, y nada sin id', () => {
    const id = signal<number | null>(null);
    const pagina = signal(0);
    TestBed.runInInjectionContext(() => {
      servicio.producto(id);
      servicio.historial(id, pagina);
    });
    TestBed.tick();
    http.expectNone(() => true);

    id.set(7);
    TestBed.tick();
    http.expectOne('/api/inventario/7').flush(TALADRO);
    http.expectOne((pedido) => pedido.url === '/api/inventario/7/movimientos').flush(PAGINA);

    pagina.set(1);
    TestBed.tick();
    const segunda = http.expectOne((pedido) => pedido.url === '/api/inventario/7/movimientos');
    expect(segunda.request.params.get('pagina')).toBe('1');
    segunda.flush(PAGINA);
  });

  // HU-08 RF-10: las categorías del filtro, también las ocultas
  it('pide las categorías del inventario', () => {
    TestBed.runInInjectionContext(() => servicio.categorias());
    TestBed.tick();

    http.expectOne('/api/inventario/categorias').flush([]);
  });

  // HU-08 RF-3
  it('registra una entrada sin nota vacía', async () => {
    const promesa = servicio.registrarEntrada(7, 10, '   ');

    const peticion = http.expectOne('/api/inventario/7/entradas');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ cantidad: 10, nota: null });
    peticion.flush(TALADRO, { status: 201, statusText: 'Created' });

    await expect(promesa).resolves.toEqual(TALADRO);
  });

  // HU-08 RF-4, RF-7
  it('registra un ajuste con la cantidad contada, el motivo y la nota', async () => {
    const promesa = servicio.registrarAjuste(7, 8, 'OTRO', ' Se usó en el taller ');

    const peticion = http.expectOne('/api/inventario/7/ajustes');
    expect(peticion.request.body).toEqual({ contada: 8, motivo: 'OTRO', nota: 'Se usó en el taller' });
    peticion.flush(TALADRO, { status: 201, statusText: 'Created' });

    await promesa;
  });

  // HU-08 RF-9
  it('cambia y quita el mínimo', async () => {
    const promesa = servicio.cambiarMinimo(7, null);

    const peticion = http.expectOne('/api/inventario/7/minimo');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ minimo: null });
    peticion.flush(TALADRO);

    await promesa;
  });

  // HU-08 RF-6: el 409 llega con su código para el diccionario
  it('rechaza un ajuste sin cambio con su código', async () => {
    const promesa = servicio.registrarAjuste(7, 10, 'CONTEO', '');

    http.expectOne('/api/inventario/7/ajustes').flush(
      { type: 'about:blank', title: 'Conflict', status: 409, detail: 'x', codigo: 'AJUSTE_SIN_CAMBIO' },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'AJUSTE_SIN_CAMBIO' });
  });
});
