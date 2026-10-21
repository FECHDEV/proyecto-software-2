import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Carrito } from '../modelos/carrito';
import { Rol, Usuario } from '../modelos/usuario';
import { SesionService } from '../sesion/sesion.service';
import { CarritoService } from './carrito.service';

// Copia de lo que responde CarritoController (CarritoIntegracionTest)
const CON_MARTILLO: Carrito = {
  lineas: [
    {
      idProducto: 7,
      nombre: 'Martillo',
      imagen: null,
      precio: 85.5,
      unidad: 'UNIDAD',
      cantidad: 2,
      subtotal: 171,
      estado: 'DISPONIBLE',
    },
  ],
  total: 171,
  cantidadLineas: 1,
  idPedidoPendiente: null,
  correoVerificado: true,
};

const VACIO: Carrito = { lineas: [], total: 0, cantidadLineas: 0, idPedidoPendiente: null, correoVerificado: true };

function usuario(idUsuario: number, rol: Rol): Usuario {
  return {
    idUsuario,
    nombre: 'Ana',
    apellido: 'Paz',
    correo: `ana${idUsuario}@mail.com`,
    telefono: null,
    rol,
    estado: 'ACTIVA',
    fechaRegistro: '2026-10-17T09:00:00',
    correoVerificado: true,
  };
}

// HU-11: llamadas a /api/carrito
describe('CarritoService', () => {
  let http: HttpTestingController;
  const cuenta = signal<Usuario | null>(null);

  function servicio(): CarritoService {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: SesionService,
          useValue: { usuario: cuenta, rol: () => cuenta()?.rol ?? null },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    return TestBed.inject(CarritoService);
  }

  // httpResource entrega la respuesta después del flush
  const estable = () => TestBed.inject(ApplicationRef).whenStable();

  beforeEach(() => cuenta.set(null));

  afterEach(() => http.verify());

  // HU-11 RF-7
  it('sin sesión o con el personal no pide el carrito', () => {
    const carrito = servicio();
    TestBed.tick();
    http.expectNone('/api/carrito');
    expect(carrito.cantidadLineas()).toBe(0);

    for (const rol of ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] as const) {
      cuenta.set(usuario(1, rol));
      TestBed.tick();
      http.expectNone('/api/carrito');
    }
    expect(carrito.cantidadLineas()).toBe(0);
  });

  // HU-11 RF-13
  it('con un cliente pide su carrito', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();

    http.expectOne({ method: 'GET', url: '/api/carrito' }).flush(CON_MARTILLO);
    await estable();

    expect(carrito.carrito.value()).toEqual(CON_MARTILLO);
    expect(carrito.cantidadLineas()).toBe(1);
  });

  // HU-11 RF-1
  it('agregar manda el producto y la cantidad y deja la respuesta', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(VACIO);

    const agregado = carrito.agregar(7, 2);
    const pedido = http.expectOne({ method: 'POST', url: '/api/carrito/lineas' });
    expect(pedido.request.body).toEqual({ idProducto: 7, cantidad: 2 });
    pedido.flush(CON_MARTILLO);

    await expect(agregado).resolves.toEqual(CON_MARTILLO);
    expect(carrito.cantidadLineas()).toBe(1);
    http.expectNone('/api/carrito');
  });

  // HU-11 RF-4
  it('si la API rechaza, falla con el código y el carrito no cambia', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(CON_MARTILLO);
    await estable();

    const agregado = carrito.agregar(9, 1);
    http
      .expectOne('/api/carrito/lineas')
      .flush(
        { status: 409, codigo: 'PRODUCTO_AGOTADO', detail: 'El producto está agotado.' },
        { status: 409, statusText: 'Conflict' },
      );

    await expect(agregado).rejects.toMatchObject({ status: 409, codigo: 'PRODUCTO_AGOTADO' });
    expect(carrito.cantidadLineas()).toBe(1);
  });

  // HU-11 RF-10, RF-11
  it('cambiar, quitar y vaciar usan la API y dejan la respuesta', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(CON_MARTILLO);

    const cambiado = carrito.cambiarCantidad(7, 3);
    const cambio = http.expectOne({ method: 'PUT', url: '/api/carrito/lineas/7' });
    expect(cambio.request.body).toEqual({ cantidad: 3 });
    cambio.flush(CON_MARTILLO);
    await cambiado;

    const quitado = carrito.quitar(7);
    http.expectOne({ method: 'DELETE', url: '/api/carrito/lineas/7' }).flush(VACIO);
    await quitado;
    expect(carrito.cantidadLineas()).toBe(0);

    const vaciado = carrito.vaciar();
    http.expectOne({ method: 'DELETE', url: '/api/carrito' }).flush(VACIO);
    await expect(vaciado).resolves.toEqual(VACIO);
  });

  // HU-11 RF-13
  it('al cerrar sesión queda vacío y otro cliente pide el suyo', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(CON_MARTILLO);
    await estable();
    expect(carrito.cantidadLineas()).toBe(1);

    cuenta.set(null);
    TestBed.tick();
    expect(carrito.cantidadLineas()).toBe(0);

    cuenta.set(usuario(2, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(VACIO);
    await estable();
    expect(carrito.cantidadLineas()).toBe(0);
  });

  // Revisión de HU-11 (/code-review): dos escrituras seguidas se aplican en orden, y la respuesta
  // más vieja no pisa a la nueva
  it('las escrituras van una después de la otra', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(CON_MARTILLO);
    await estable();

    const cambiado = carrito.cambiarCantidad(7, 3);
    const quitado = carrito.quitar(7);
    const cambio = http.expectOne({ method: 'PUT', url: '/api/carrito/lineas/7' });
    http.expectNone({ method: 'DELETE', url: '/api/carrito/lineas/7' });
    cambio.flush(CON_MARTILLO);
    await cambiado;

    http.expectOne({ method: 'DELETE', url: '/api/carrito/lineas/7' }).flush(VACIO);
    await quitado;
    expect(carrito.cantidadLineas()).toBe(0);
  });

  // Revisión de HU-11 (/code-review): si una escritura falla, la siguiente igual se manda
  it('una escritura rechazada no traba las siguientes', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(CON_MARTILLO);

    const rechazado = carrito.cambiarCantidad(7, 3);
    const quitado = carrito.quitar(7);
    http
      .expectOne({ method: 'PUT', url: '/api/carrito/lineas/7' })
      .flush({ status: 404, codigo: 'PRODUCTO_NO_ENCONTRADO', detail: '' }, { status: 404, statusText: 'Not Found' });
    await expect(rechazado).rejects.toMatchObject({ status: 404 });

    http.expectOne({ method: 'DELETE', url: '/api/carrito/lineas/7' }).flush(VACIO);
    await expect(quitado).resolves.toEqual(VACIO);
  });

  // Revisión de HU-11 (/code-review): sin carrito cargado no se sabe cuántos hay
  it('si el carrito no cargó, la cantidad es desconocida', async () => {
    const carrito = servicio();
    cuenta.set(usuario(1, 'CLIENTE'));
    TestBed.tick();
    http.expectOne('/api/carrito').flush(null, { status: 500, statusText: 'Error' });
    await estable();

    expect(carrito.cantidadLineas()).toBeNull();
  });
});
