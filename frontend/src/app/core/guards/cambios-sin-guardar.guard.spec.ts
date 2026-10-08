import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { afterEach, vi } from 'vitest';

import { Sesion } from '../modelos/sesion';
import { SesionService } from '../sesion/sesion.service';
import { cambiosSinGuardarGuard, ConCambiosSinGuardar } from './cambios-sin-guardar.guard';

const SESION: Sesion = {
  token: 'jwt.de.prueba',
  tipo: 'Bearer',
  expiracion: new Date(Date.now() + 3_600_000).toISOString(),
  usuario: {
    idUsuario: 2,
    nombre: 'Ana',
    apellido: 'Rojas',
    correo: 'ana@mail.com',
    telefono: null,
    rol: 'CLIENTE',
    estado: 'ACTIVA',
    fechaRegistro: '2026-09-14T10:00:00',
  },
};

function pantalla(conCambios: boolean): ConCambiosSinGuardar {
  return { tieneCambiosSinGuardar: () => conCambios };
}

function salir(componente: ConCambiosSinGuardar): unknown {
  return TestBed.runInInjectionContext(() =>
    cambiosSinGuardarGuard(
      componente,
      {} as ActivatedRouteSnapshot,
      {} as RouterStateSnapshot,
      {} as RouterStateSnapshot,
    ),
  );
}

describe('cambiosSinGuardarGuard', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.inject(SesionService).iniciar(SESION);
  });

  afterEach(() => vi.restoreAllMocks());

  it('sin cambios deja salir sin preguntar', () => {
    const confirmar = vi.spyOn(window, 'confirm');

    expect(salir(pantalla(false))).toBe(true);
    expect(confirmar).not.toHaveBeenCalled();
  });

  it('con cambios pregunta y respeta la respuesta', () => {
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true);

    expect(salir(pantalla(true))).toBe(false);
    expect(salir(pantalla(true))).toBe(true);
  });

  // Al cerrar sesión o al caer por un 401 no hay nada que guardar: preguntar
  // dejaría a la persona en una pantalla sin sesión
  it('sin sesión deja salir sin preguntar', () => {
    TestBed.inject(SesionService).cerrar();
    const confirmar = vi.spyOn(window, 'confirm');

    expect(salir(pantalla(true))).toBe(true);
    expect(confirmar).not.toHaveBeenCalled();
  });
});
