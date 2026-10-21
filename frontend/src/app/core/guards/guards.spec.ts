import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  GuardResult,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';

import { Sesion } from '../modelos/sesion';
import { Rol } from '../modelos/usuario';
import { SesionService } from '../sesion/sesion.service';
import { rolGuard } from './rol.guard';
import { sesionGuard } from './sesion.guard';
import { visitanteGuard } from './visitante.guard';

function sesionCon(rol: Rol): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: {
      idUsuario: 2,
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      telefono: null,
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
      correoVerificado: true,
    },
  };
}

function ruta(datos: Record<string, unknown> = {}): ActivatedRouteSnapshot {
  return { data: datos } as unknown as ActivatedRouteSnapshot;
}

function estado(url: string): RouterStateSnapshot {
  return { url } as RouterStateSnapshot;
}

describe('guards de ruta', () => {
  let sesion: SesionService;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    sesion = TestBed.inject(SesionService);
    router = TestBed.inject(Router);
  });

  function correr(guard: () => GuardResult, datos?: Record<string, unknown>, url = '/mi-cuenta') {
    return TestBed.runInInjectionContext(() =>
      (guard as unknown as (r: ActivatedRouteSnapshot, e: RouterStateSnapshot) => GuardResult)(
        ruta(datos),
        estado(url),
      ),
    );
  }

  describe('sesionGuard', () => {
    it('deja pasar con sesión', () => {
      sesion.iniciar(sesionCon('CLIENTE'));

      expect(correr(sesionGuard as never)).toBe(true);
    });

    it('sin sesión manda a iniciar sesión y recuerda el destino', () => {
      const resultado = correr(sesionGuard as never);

      expect(resultado).toBeInstanceOf(UrlTree);
      expect(router.serializeUrl(resultado as UrlTree)).toContain('/inicio-sesion');
      expect(router.serializeUrl(resultado as UrlTree)).toContain('destino=%2Fmi-cuenta');
    });
  });

  describe('rolGuard', () => {
    // Ocultar una pantalla es usabilidad: quien autoriza de verdad es el backend
    it('deja pasar al rol que la ruta pide', () => {
      sesion.iniciar(sesionCon('ADMINISTRADOR'));

      expect(correr(rolGuard as never, { rol: 'ADMINISTRADOR' }, '/usuarios')).toBe(true);
    });

    it('bloquea a otro rol', () => {
      sesion.iniciar(sesionCon('CLIENTE'));

      const resultado = correr(rolGuard as never, { rol: 'ADMINISTRADOR' }, '/usuarios');

      expect(resultado).toBeInstanceOf(UrlTree);
      expect(router.serializeUrl(resultado as UrlTree)).toBe('/');
    });

    // HU-08 RF-1: el inventario es de tres roles
    it('con una lista de roles deja pasar a cualquiera de ellos', () => {
      const datos = { roles: ['EMPLEADO', 'ADMINISTRADOR', 'CONTADOR'] };
      sesion.iniciar(sesionCon('CONTADOR'));
      expect(correr(rolGuard as never, datos, '/inventario')).toBe(true);

      sesion.iniciar(sesionCon('CLIENTE'));
      const resultado = correr(rolGuard as never, datos, '/inventario');
      expect(router.serializeUrl(resultado as UrlTree)).toBe('/');
    });

    // HU-09 RF-16: el inicio del personal es el panel; el del cliente, la tienda
    it('sin permiso va al panel o a la tienda', () => {
      sesion.iniciar(sesionCon('EMPLEADO'));
      const empleado = correr(rolGuard as never, { rol: 'ADMINISTRADOR' }, '/usuarios');
      expect(router.serializeUrl(empleado as UrlTree)).toBe('/panel');

      sesion.iniciar(sesionCon('CLIENTE'));
      const cliente = correr(rolGuard as never, { rol: 'ADMINISTRADOR' }, '/usuarios');
      expect(router.serializeUrl(cliente as UrlTree)).toBe('/');
    });

    it('sin sesión manda a iniciar sesión', () => {
      const resultado = correr(rolGuard as never, { rol: 'ADMINISTRADOR' }, '/usuarios');

      expect(router.serializeUrl(resultado as UrlTree)).toContain('/inicio-sesion');
    });
  });

  // HU-01 RF-14: «Crear cuenta» es para quien todavía no tiene sesión
  describe('visitanteGuard', () => {
    it('deja pasar sin sesión', () => {
      expect(correr(visitanteGuard as never)).toBe(true);
    });

    it('con sesión lleva al inicio', () => {
      sesion.iniciar(sesionCon('CLIENTE'));

      const resultado = correr(visitanteGuard as never) as UrlTree;

      expect(router.serializeUrl(resultado)).toBe('/');
    });

    // HU-09 RF-16
    it('con sesión va al panel o a la tienda', () => {
      sesion.iniciar(sesionCon('CONTADOR'));

      expect(router.serializeUrl(correr(visitanteGuard as never) as UrlTree)).toBe('/panel');
    });
  });
});
