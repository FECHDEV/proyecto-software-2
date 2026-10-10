import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Pagina } from '../modelos/pagina';
import { FiltrosDeUsuarios, UsuarioResumen } from '../modelos/usuario-resumen';
import { UsuariosService } from './usuarios.service';

// Copia de lo que responde el backend en UsuariosControllerTest
const ANA: UsuarioResumen = {
  idUsuario: 7,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-12T11:00:00',
};

const PAGINA: Pagina<UsuarioResumen> = {
  contenido: [ANA],
  pagina: 0,
  tamano: 20,
  totalElementos: 21,
  totalPaginas: 2,
};

const SIN_FILTROS: FiltrosDeUsuarios = { rol: null, estado: null, texto: '', pagina: 0 };

describe('UsuariosService', () => {
  let servicio: UsuariosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(UsuariosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function estable() {
    await TestBed.inject(ApplicationRef).whenStable();
  }

  // HU-05 RF-2: la primera página, sin filtros que el backend no pidió
  it('pide la lista sin parámetros de más', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.listado(signal(SIN_FILTROS)));
    TestBed.tick();

    const peticion = http.expectOne((pedido) => pedido.url === '/api/usuarios');
    expect(peticion.request.method).toBe('GET');
    expect(peticion.request.params.keys()).toEqual([]);
    peticion.flush(PAGINA);
    await estable();

    expect(recurso.value()).toEqual(PAGINA);
  });

  // HU-05 RF-3: rol, estado, texto y página viajan como parámetros
  it('envía los filtros y la página', async () => {
    TestBed.runInInjectionContext(() =>
      servicio.listado(signal({ rol: 'CLIENTE', estado: 'ACTIVA', texto: '  ana ', pagina: 1 })),
    );
    TestBed.tick();

    const peticion = http.expectOne((pedido) => pedido.url === '/api/usuarios');
    expect(peticion.request.params.get('rol')).toBe('CLIENTE');
    expect(peticion.request.params.get('estado')).toBe('ACTIVA');
    expect(peticion.request.params.get('texto')).toBe('ana');
    expect(peticion.request.params.get('pagina')).toBe('1');
    peticion.flush(PAGINA);
    await estable();
  });

  // Un texto de solo espacios no filtra (caso límite de la gestión de usuarios)
  it('no envía un texto de solo espacios', async () => {
    TestBed.runInInjectionContext(() => servicio.listado(signal({ ...SIN_FILTROS, texto: '   ' })));
    TestBed.tick();

    const peticion = http.expectOne((pedido) => pedido.url === '/api/usuarios');
    expect(peticion.request.params.has('texto')).toBe(false);
    peticion.flush(PAGINA);
    await estable();
  });

  it('vuelve a pedir la lista cuando cambian los filtros', async () => {
    const filtros = signal(SIN_FILTROS);
    TestBed.runInInjectionContext(() => servicio.listado(filtros));
    TestBed.tick();
    http.expectOne((pedido) => pedido.url === '/api/usuarios').flush(PAGINA);
    await estable();

    filtros.set({ ...SIN_FILTROS, rol: 'EMPLEADO' });
    TestBed.tick();

    const segunda = http.expectOne((pedido) => pedido.url === '/api/usuarios');
    expect(segunda.request.params.get('rol')).toBe('EMPLEADO');
    segunda.flush(PAGINA);
    await estable();
  });

  // HU-05 RF-4
  it('cambia el rol y devuelve el usuario actualizado', async () => {
    const promesa = servicio.cambiarRol(7, 'EMPLEADO');

    const peticion = http.expectOne('/api/usuarios/7/rol');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ rol: 'EMPLEADO' });
    peticion.flush({ ...ANA, rol: 'EMPLEADO' });

    await expect(promesa).resolves.toEqual({ ...ANA, rol: 'EMPLEADO' });
  });

  // HU-05 RF-5, RF-6
  it('cambia el estado y devuelve el usuario actualizado', async () => {
    const promesa = servicio.cambiarEstado(7, 'DESACTIVADA');

    const peticion = http.expectOne('/api/usuarios/7/estado');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ estado: 'DESACTIVADA' });
    peticion.flush({ ...ANA, estado: 'DESACTIVADA' });

    await expect(promesa).resolves.toEqual({ ...ANA, estado: 'DESACTIVADA' });
  });

  // HU-05 RF-12: los 409 llegan con su código para el diccionario
  it('devuelve el código de una operación rechazada', async () => {
    const promesa = servicio.cambiarEstado(1, 'DESACTIVADA');

    http.expectOne('/api/usuarios/1/estado').flush(
      { status: 409, detail: 'x', codigo: 'ULTIMO_ADMINISTRADOR_ACTIVO' },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'ULTIMO_ADMINISTRADOR_ACTIVO' });
  });

  it('sin red avisa que no hubo conexión', async () => {
    const promesa = servicio.cambiarRol(7, 'EMPLEADO');

    http.expectOne('/api/usuarios/7/rol').error(new ProgressEvent('error'));

    await expect(promesa).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
