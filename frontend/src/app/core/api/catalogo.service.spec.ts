import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { aTarjeta, idDeProducto, ProductoEnTienda, SIN_FILTROS, urlDeImagen, urlDeModelo } from '../modelos/catalogo';
import { CatalogoService } from './catalogo.service';

const PAGINA = { contenido: [], pagina: 0, tamano: 24, totalElementos: 0, totalPaginas: 0 };

// Copia de lo que responde ProductosController (CatalogoIntegracionTest)
const MANGUERA: ProductoEnTienda = {
  idProducto: 9,
  nombre: 'Manguera reforzada',
  marca: null,
  precio: 12.5,
  unidad: 'METRO',
  imagenPrincipal: 'b.webp',
  disponible: false,
  conModelo: false,
};

// HU-09: llamadas al catálogo público
describe('CatalogoService', () => {
  let servicio: CatalogoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(CatalogoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-09 RF-9, RF-11
  it('pide el listado con los filtros puestos', () => {
    TestBed.runInInjectionContext(() => servicio.listado(signal(SIN_FILTROS)));
    TestBed.tick();
    const sinFiltros = http.expectOne((pedido) => pedido.url === '/api/productos');
    expect(sinFiltros.request.params.keys()).toEqual([]);
    sinFiltros.flush(PAGINA);

    TestBed.runInInjectionContext(() =>
      servicio.listado(
        signal({
          q: ' martillo ',
          categoria: 4,
          marca: 'Truper',
          precioMin: 10,
          precioMax: 99.5,
          disponibles: true,
          orden: 'PRECIO_ASC' as const,
          pagina: 2,
        }),
      ),
    );
    TestBed.tick();
    const conFiltros = http.expectOne((pedido) => pedido.url === '/api/productos');
    const params = conFiltros.request.params;
    expect(params.get('q')).toBe('martillo');
    expect(params.get('categoria')).toBe('4');
    expect(params.get('marca')).toBe('Truper');
    expect(params.get('precioMin')).toBe('10');
    expect(params.get('precioMax')).toBe('99.5');
    expect(params.get('disponibles')).toBe('true');
    expect(params.get('orden')).toBe('PRECIO_ASC');
    expect(params.get('pagina')).toBe('2');
    conFiltros.flush(PAGINA);
  });

  // HU-09 RF-3
  it('pide la portada y las marcas', () => {
    TestBed.runInInjectionContext(() => {
      servicio.portada();
      servicio.marcas();
    });
    TestBed.tick();

    http.expectOne('/api/productos/portada').flush({ destacados: [], recientes: [], categorias: [] });
    http.expectOne('/api/productos/marcas').flush([]);
  });

  // HU-09 RF-6, RF-7
  it('arma la tarjeta: unidad en minúsculas, foto, agotado y enlace al detalle', () => {
    expect(aTarjeta(MANGUERA)).toEqual({
      nombre: 'Manguera reforzada',
      marca: null,
      precio: 12.5,
      unidad: 'metro',
      imagen: '/api/productos/9/imagenes/b.webp',
      agotado: true,
      enlace: '/producto/9',
      conModelo: false,
    });
    // HU-16 RF-2
    expect(aTarjeta({ ...MANGUERA, conModelo: true }).conModelo).toBe(true);
    expect(aTarjeta({ ...MANGUERA, unidad: 'UNIDAD', imagenPrincipal: null }).unidad).toBeNull();
    expect(aTarjeta({ ...MANGUERA, imagenPrincipal: null }).imagen).toBeNull();
  });

  // HU-10 RF-1
  it('pide el detalle del producto', () => {
    const id = signal<number | null>(7);
    TestBed.runInInjectionContext(() => servicio.detalle(id));
    TestBed.tick();

    http.expectOne('/api/productos/7').flush({});
  });

  // HU-10 RF-9: sin un número válido no se consulta la API
  it('sin id no pide nada', () => {
    TestBed.runInInjectionContext(() => servicio.detalle(signal(null)));
    TestBed.tick();

    http.expectNone(() => true);
  });

  // HU-10 RF-9
  it('idDeProducto descarta lo que no es un número de producto', () => {
    expect(idDeProducto('7')).toBe(7);
    for (const basura of [null, '', 'abc', '-1', '0', '1.5', '7abc', ' 7', '1e3', '99999999999999999999']) {
      expect(idDeProducto(basura)).toBeNull();
    }
  });

  // HU-10 RF-5
  it('arma la dirección de cada foto', () => {
    expect(urlDeImagen(9, 'b.webp')).toBe('/api/productos/9/imagenes/b.webp');
    // Revisión de HU-10: el nombre va codificado, como en la gestión
    expect(urlDeImagen(9, 'a b#1.webp')).toBe('/api/productos/9/imagenes/a%20b%231.webp');
  });

  // HU-16 RF-1: la misma regla que la gestión (HU-15)
  it('arma la dirección del modelo', () => {
    expect(urlDeModelo(9, 'm.glb')).toBe('/api/productos/9/modelo/m.glb');
    expect(urlDeModelo(9, 'a b#1.glb')).toBe('/api/productos/9/modelo/a%20b%231.glb');
  });
});
