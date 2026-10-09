import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ErrorApi } from '../modelos/error-api';
import { Sesion } from '../modelos/sesion';
import { Usuario } from '../modelos/usuario';
import { CuentaService } from './cuenta.service';

// Copia de lo que responde el backend en CuentaControllerTest y
// DatosPersonalesIntegracionTest
const ANA: Usuario = {
  idUsuario: 2,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  telefono: '70000000',
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-14T10:00:00',
};

const SESION_NUEVA: Sesion = {
  token: 'jwt.nuevo',
  tipo: 'Bearer',
  expiracion: '2026-09-18T20:00:00Z',
  usuario: ANA,
};

const DATOS_INVALIDOS: ErrorApi = {
  status: 400,
  detail: 'Los datos enviados no son válidos.',
  codigo: 'DATOS_INVALIDOS',
  errores: [{ campo: 'nombre', mensaje: 'es obligatorio' }],
};

describe('CuentaService', () => {
  let servicio: CuentaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(CuentaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-04 RF-1: los datos de la cuenta de la sesión
  it('lee los datos personales', async () => {
    const recurso = TestBed.runInInjectionContext(() => servicio.datosPersonales());
    TestBed.tick();

    const peticion = http.expectOne('/api/cuenta/datos-personales');
    expect(peticion.request.method).toBe('GET');
    peticion.flush(ANA);
    await TestBed.inject(ApplicationRef).whenStable();

    expect(recurso.value()).toEqual(ANA);
  });

  // HU-04 RF-2, RF-3: se envía limpio y el teléfono vacío viaja como null
  it('actualiza nombre, apellido y teléfono', async () => {
    const promesa = servicio.actualizarDatos({ nombre: ' Ana ', apellido: 'Rojas ', telefono: '  ' });

    const peticion = http.expectOne('/api/cuenta/datos-personales');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Ana', apellido: 'Rojas', telefono: null });
    peticion.flush(ANA);

    await expect(promesa).resolves.toEqual(ANA);
  });

  // HU-04 RF-4: nada de correo, rol ni estado en la petición
  it('no envía datos que no se pueden modificar', async () => {
    const promesa = servicio.actualizarDatos({ nombre: 'Ana', apellido: 'Rojas', telefono: '70000000' });

    const peticion = http.expectOne('/api/cuenta/datos-personales');
    expect(Object.keys(peticion.request.body).sort()).toEqual(['apellido', 'nombre', 'telefono']);
    peticion.flush(ANA);
    await promesa;
  });

  it('devuelve los errores por campo del backend', async () => {
    const promesa = servicio.actualizarDatos({ nombre: '', apellido: 'Rojas', telefono: '' });

    http
      .expectOne('/api/cuenta/datos-personales')
      .flush(DATOS_INVALIDOS, { status: 400, statusText: 'Bad Request' });

    await expect(promesa).rejects.toEqual(DATOS_INVALIDOS);
  });

  // HU-04 RF-5: la contraseña viaja tal cual y vuelve una sesión con token nuevo
  it('cambia la contraseña y devuelve la sesión nueva', async () => {
    const promesa = servicio.cambiarContrasena({ contrasenaActual: ' actual ', contrasenaNueva: 'nueva-clave' });

    const peticion = http.expectOne('/api/cuenta/contrasena');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ contrasenaActual: ' actual ', contrasenaNueva: 'nueva-clave' });
    peticion.flush(SESION_NUEVA);

    await expect(promesa).resolves.toEqual(SESION_NUEVA);
  });

  // HU-04 RF-7
  it('rechaza con la contraseña actual incorrecta', async () => {
    const promesa = servicio.cambiarContrasena({ contrasenaActual: 'otra', contrasenaNueva: 'nueva-clave' });

    http.expectOne('/api/cuenta/contrasena').flush(
      { status: 400, detail: 'La contraseña actual no es correcta.', codigo: 'CONTRASENA_ACTUAL_INCORRECTA' },
      { status: 400, statusText: 'Bad Request' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'CONTRASENA_ACTUAL_INCORRECTA' });
  });

  // HU-04 RF-7
  it('rechaza mientras el correo está bloqueado', async () => {
    const promesa = servicio.cambiarContrasena({ contrasenaActual: 'actual-1', contrasenaNueva: 'nueva-clave' });

    http.expectOne('/api/cuenta/contrasena').flush(
      { status: 429, detail: 'Demasiados intentos fallidos.', codigo: 'INTENTOS_EXCEDIDOS' },
      { status: 429, statusText: 'Too Many Requests' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'INTENTOS_EXCEDIDOS' });
  });

  it('sin red avisa que no hubo conexión', async () => {
    const promesa = servicio.cambiarContrasena({ contrasenaActual: 'actual-1', contrasenaNueva: 'nueva-clave' });

    http.expectOne('/api/cuenta/contrasena').error(new ProgressEvent('error'));

    await expect(promesa).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
