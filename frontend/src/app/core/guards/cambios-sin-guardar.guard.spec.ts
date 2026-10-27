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
    correoVerificado: true,
  },
};

let respuestas: boolean[] = [];
const preguntas = vi.fn(() => Promise.resolve(respuestas.shift() ?? false));

function pantalla(conCambios: boolean): ConCambiosSinGuardar {
  return { tieneCambiosSinGuardar: () => conCambios, confirmarSalida: preguntas };
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
    respuestas = [];
    preguntas.mockClear();
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.inject(SesionService).iniciar(SESION);
  });

  afterEach(() => vi.restoreAllMocks());

  // HU-04 RF-9
  it('sin cambios deja salir sin preguntar', () => {
    expect(salir(pantalla(false))).toBe(true);
    expect(preguntas).not.toHaveBeenCalled();
  });

  // HU-04 RF-9
  // Revisión total: con el diálogo de la pantalla, no con window.confirm
  it('con cambios pregunta y respeta la respuesta', async () => {
    respuestas = [false, true];

    await expect(salir(pantalla(true))).resolves.toBe(false);
    await expect(salir(pantalla(true))).resolves.toBe(true);
    expect(preguntas).toHaveBeenCalledTimes(2);
  });

  // Al cerrar sesión o al caer por un 401 no hay nada que guardar: preguntar
  // dejaría a la persona en una pantalla sin sesión
  // HU-04 RF-9
  it('sin sesión deja salir sin preguntar', () => {
    TestBed.inject(SesionService).cerrar();

    expect(salir(pantalla(true))).toBe(true);
    expect(preguntas).not.toHaveBeenCalled();
  });
});
