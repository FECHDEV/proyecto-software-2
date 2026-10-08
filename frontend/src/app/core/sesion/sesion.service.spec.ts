import { TestBed } from '@angular/core/testing';
import { afterEach, vi } from 'vitest';

import { Sesion } from '../modelos/sesion';
import { CLAVE_SESION, guardarSesion } from './almacen-token';
import { SesionService } from './sesion.service';

function sesion(expiracion: string, rol: 'CLIENTE' | 'ADMINISTRADOR' = 'CLIENTE'): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion,
    usuario: {
      idUsuario: 2,
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      telefono: null,
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
    },
  };
}

function vigente(rol: 'CLIENTE' | 'ADMINISTRADOR' = 'CLIENTE'): Sesion {
  return sesion(new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(), rol);
}

describe('SesionService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('arranca sin sesión', () => {
    const servicio = TestBed.inject(SesionService);

    expect(servicio.autenticado()).toBe(false);
    expect(servicio.usuario()).toBeNull();
    expect(servicio.token()).toBeNull();
  });

  it('restaura la sesión guardada al construirse', () => {
    guardarSesion(vigente());

    const servicio = TestBed.inject(SesionService);

    expect(servicio.autenticado()).toBe(true);
    expect(servicio.usuario()?.correo).toBe('ana@mail.com');
    expect(servicio.rol()).toBe('CLIENTE');
  });

  it('no restaura una sesión vencida', () => {
    guardarSesion(sesion('2020-01-01T00:00:00Z'));

    const servicio = TestBed.inject(SesionService);

    expect(servicio.autenticado()).toBe(false);
  });

  it('iniciar deja la sesión activa y la persiste', () => {
    const servicio = TestBed.inject(SesionService);

    servicio.iniciar(vigente());

    expect(servicio.autenticado()).toBe(true);
    expect(servicio.token()).toBe('jwt.de.prueba');
    expect(localStorage.getItem(CLAVE_SESION)).toContain('ana@mail.com');
  });

  it('cerrar limpia la sesión y el almacenamiento', () => {
    const servicio = TestBed.inject(SesionService);
    servicio.iniciar(vigente());

    servicio.cerrar();

    expect(servicio.autenticado()).toBe(false);
    expect(servicio.usuario()).toBeNull();
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
  });

  // Una pestaña abierta no puede seguir diciendo que hay sesión tras el vencimiento
  it('cierra la sesión sola cuando el token vence', () => {
    vi.useFakeTimers();
    const servicio = TestBed.inject(SesionService);
    servicio.iniciar(sesion(new Date(Date.now() + 60_000).toISOString()));

    vi.advanceTimersByTime(60_001);

    expect(servicio.autenticado()).toBe(false);
    vi.useRealTimers();
  });

  it('antes del vencimiento la sesión sigue abierta', () => {
    vi.useFakeTimers();
    const servicio = TestBed.inject(SesionService);
    servicio.iniciar(sesion(new Date(Date.now() + 60_000).toISOString()));

    vi.advanceTimersByTime(59_000);

    expect(servicio.autenticado()).toBe(true);
    vi.useRealTimers();
  });

  it('reconoce al administrador', () => {
    const servicio = TestBed.inject(SesionService);

    servicio.iniciar(vigente('ADMINISTRADOR'));

    expect(servicio.esAdministrador()).toBe(true);
  });

  it('un cliente no es administrador', () => {
    const servicio = TestBed.inject(SesionService);

    servicio.iniciar(vigente());

    expect(servicio.esAdministrador()).toBe(false);
  });
});

// los datos guardados reemplazan a los de la sesión, para que el
// encabezado muestre el nombre nuevo sin volver a iniciar sesión
describe('SesionService.actualizarUsuario', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  it('reemplaza el usuario y conserva el token', () => {
    const servicio = TestBed.inject(SesionService);
    const inicial = vigente();
    servicio.iniciar(inicial);

    servicio.actualizarUsuario({ ...inicial.usuario, nombre: 'Ana María' });

    expect(servicio.usuario()?.nombre).toBe('Ana María');
    expect(servicio.token()).toBe('jwt.de.prueba');
    const guardada = JSON.parse(localStorage.getItem(CLAVE_SESION) ?? '{}') as Sesion;
    expect(guardada.usuario.nombre).toBe('Ana María');
    expect(guardada.token).toBe('jwt.de.prueba');
  });

  it('sin sesión no hace nada', () => {
    const servicio = TestBed.inject(SesionService);

    servicio.actualizarUsuario(vigente().usuario);

    expect(servicio.autenticado()).toBe(false);
    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
  });
});
