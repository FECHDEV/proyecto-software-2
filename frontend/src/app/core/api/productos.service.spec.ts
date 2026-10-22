import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Pagina } from '../modelos/pagina';
import { DatosDeProducto, FiltrosDeProductos, Producto, ProductoResumen } from '../modelos/producto';
import { ProductosService } from './productos.service';

// Copia de lo que responde GestionProductosController (ProductosIntegracionTest)
const TALADRO: Producto = {
  idProducto: 7,
  codigo: 'HER-0007',
  nombre: 'Taladro percutor 750 W',
  marca: 'Bosch',
  descripcion: null,
  precio: 420,
  costo: 300,
  unidad: 'UNIDAD',
  idCategoria: 3,
  categoria: 'Herramientas eléctricas',
  activo: true,
  fechaAlta: '2026-10-10T09:00:00',
  ficha: [{ nombre: 'Potencia', valor: '750 W' }],
  imagenes: ['a.png', 'b.png'],
  destacado: false,
};

const PAGINA: Pagina<ProductoResumen> = {
  contenido: [],
  pagina: 0,
  tamano: 20,
  totalElementos: 0,
  totalPaginas: 0,
};

const SIN_FILTROS: FiltrosDeProductos = { categoria: null, activo: null, texto: '', pagina: 0 };

const DATOS: DatosDeProducto = {
  nombre: '  Taladro percutor 750 W ',
  marca: '  ',
  descripcion: ' Para concreto ',
  idCategoria: '3',
  unidad: 'UNIDAD',
  precio: '420,5',
  costo: ' 300,25 ',
  ficha: [
    { nombre: ' Potencia ', valor: ' 750 W ' },
    { nombre: ' ', valor: '' },
  ],
  destacado: true,
};

// HU-07: llamadas a /api/gestion/productos
describe('ProductosService', () => {
  let servicio: ProductosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ProductosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-07 RF-10: sin filtros no manda parámetros
  it('pide la lista sin parámetros cuando no hay filtros', () => {
    TestBed.runInInjectionContext(() => servicio.listado(signal(SIN_FILTROS)));
    TestBed.tick();

    const peticion = http.expectOne((pedido) => pedido.url === '/api/gestion/productos');
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush(PAGINA);
  });

  // HU-07 RF-10
  it('manda categoría, estado, texto recortado y página', () => {
    TestBed.runInInjectionContext(() =>
      servicio.listado(signal({ categoria: 4, activo: false, texto: '  bosch ', pagina: 2 })),
    );
    TestBed.tick();

    const peticion = http.expectOne((pedido) => pedido.url === '/api/gestion/productos');
    expect(peticion.request.params.get('categoria')).toBe('4');
    expect(peticion.request.params.get('activo')).toBe('false');
    expect(peticion.request.params.get('texto')).toBe('bosch');
    expect(peticion.request.params.get('pagina')).toBe('2');
    peticion.flush(PAGINA);
  });

  // Un producto nuevo todavía no tiene nada que leer
  it('lee un producto por su id y no pide nada sin id', () => {
    const id = signal<number | null>(null);
    TestBed.runInInjectionContext(() => servicio.producto(id));
    TestBed.tick();
    http.expectNone(() => true);

    id.set(7);
    TestBed.tick();
    http.expectOne('/api/gestion/productos/7').flush(TALADRO);
  });

  // HU-07 RF-2, RF-3, RF-6: textos recortados, opcionales vacíos como null, precio con coma como número y sin pares vacíos
  it('crea con los datos limpios', async () => {
    const promesa = servicio.crear(DATOS);

    const peticion = http.expectOne('/api/gestion/productos');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      nombre: 'Taladro percutor 750 W',
      marca: null,
      descripcion: 'Para concreto',
      idCategoria: 3,
      unidad: 'UNIDAD',
      precio: 420.5,
      // HU-18 RF-19
      costo: 300.25,
      ficha: [{ nombre: 'Potencia', valor: '750 W' }],
      // HU-09 RF-4
      destacado: true,
    });
    peticion.flush(TALADRO, { status: 201, statusText: 'Created' });

    await expect(promesa).resolves.toEqual(TALADRO);
  });

  // HU-18 RF-19: el costo es opcional
  it('sin costo lo manda nulo', async () => {
    const promesa = servicio.crear({ ...DATOS, costo: '  ' });

    const peticion = http.expectOne('/api/gestion/productos');
    expect(peticion.request.body.costo).toBeNull();
    peticion.flush(TALADRO, { status: 201, statusText: 'Created' });
    await promesa;
  });

  // HU-07 RF-5
  it('edita por su identificador', async () => {
    const promesa = servicio.editar(7, DATOS);

    const peticion = http.expectOne('/api/gestion/productos/7');
    expect(peticion.request.method).toBe('PUT');
    peticion.flush(TALADRO);

    await promesa;
  });

  // HU-07 RF-9
  it('activa y desactiva', async () => {
    const promesa = servicio.cambiarActivo(7, false);

    const peticion = http.expectOne('/api/gestion/productos/7/activo');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ activo: false });
    peticion.flush({ ...TALADRO, activo: false });

    await expect(promesa).resolves.toMatchObject({ activo: false });
  });

  // HU-07 RF-7: la imagen viaja como multipart, en el campo «imagen»
  it('sube una imagen como multipart', async () => {
    const archivo = new File([new Uint8Array([0x89, 0x50])], 'foto.png', { type: 'image/png' });
    const promesa = servicio.agregarImagen(7, archivo);

    const peticion = http.expectOne('/api/gestion/productos/7/imagenes');
    expect(peticion.request.method).toBe('POST');
    expect((peticion.request.body as FormData).get('imagen')).toBe(archivo);
    peticion.flush(TALADRO);

    await promesa;
  });

  // HU-07 RF-8
  it('reordena y quita imágenes', async () => {
    const reordenado = servicio.reordenar(7, ['b.png', 'a.png']);
    const orden = http.expectOne('/api/gestion/productos/7/imagenes');
    expect(orden.request.method).toBe('PUT');
    expect(orden.request.body).toEqual({ imagenes: ['b.png', 'a.png'] });
    orden.flush(TALADRO);
    await reordenado;

    const quitado = servicio.quitarImagen(7, 'a.png');
    const borrado = http.expectOne('/api/gestion/productos/7/imagenes/a.png');
    expect(borrado.request.method).toBe('DELETE');
    borrado.flush(TALADRO);
    await quitado;
  });

  // HU-07 RF-7: el 409 llega con su código para el diccionario
  it('rechaza la sexta imagen con PRODUCTO_LLENO_DE_IMAGENES', async () => {
    const promesa = servicio.agregarImagen(7, new File([], 'foto.png'));

    http.expectOne('/api/gestion/productos/7/imagenes').flush(
      { type: 'about:blank', title: 'Conflict', status: 409, detail: 'x', codigo: 'PRODUCTO_LLENO_DE_IMAGENES' },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'PRODUCTO_LLENO_DE_IMAGENES' });
  });

  it('arma la dirección pública de una imagen', () => {
    expect(servicio.urlDeImagen(7, 'a.png')).toBe('/api/productos/7/imagenes/a.png');
  });
});
