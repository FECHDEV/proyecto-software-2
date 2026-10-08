import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { Sesion } from '../modelos/sesion';
import { SesionService } from '../sesion/sesion.service';
import { erroresInterceptor } from './errores.interceptor';

function sesionVigente(): Sesion {
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
      rol: 'CLIENTE',
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
    },
  };
}

describe('erroresInterceptor', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;
  let sesion: SesionService;
  let navegaciones: string[];

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    navegaciones = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([erroresInterceptor])),
        provideHttpClientTesting(),
        {
          provide: Router,
          useValue: {
            navigate: (ruta: unknown[]) => {
              navegaciones.push(String(ruta[0]));
              return Promise.resolve(true);
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SesionService);
  });

  afterEach(() => servidor.verify());

  // RF-8, RF-9 y RF-21: el token dejó de valer, aunque no haya vencido
  it('ante un 401 cierra la sesión y lleva a iniciar sesión', async () => {
    sesion.iniciar(sesionVigente());
    const respuesta = new Promise((_, rechazar) => {
      http.get('/api/cuenta/datos-personales').subscribe({ error: rechazar });
    });

    servidor.expectOne('/api/cuenta/datos-personales').flush(
      {
        type: 'about:blank',
        title: 'Unauthorized',
        status: 401,
        detail: 'Debe iniciar sesión para acceder a este recurso.',
        codigo: 'NO_AUTENTICADO',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(respuesta).rejects.toBeDefined();
    expect(sesion.autenticado()).toBe(false);
    expect(navegaciones).toEqual(['/inicio-sesion']);
  });

  // RF-10: la sesión sigue siendo válida, solo que ese rol no puede entrar
  it('ante un 403 no cierra la sesión', async () => {
    sesion.iniciar(sesionVigente());
    const respuesta = new Promise((_, rechazar) => {
      http.get('/api/usuarios').subscribe({ error: rechazar });
    });

    servidor.expectOne('/api/usuarios').flush(
      {
        type: 'about:blank',
        title: 'Forbidden',
        status: 403,
        detail: 'No tiene permiso para acceder a este recurso.',
        codigo: 'ACCESO_DENEGADO',
      },
      { status: 403, statusText: 'Forbidden' },
    );

    await expect(respuesta).rejects.toBeDefined();
    expect(sesion.autenticado()).toBe(true);
    expect(navegaciones).toEqual([]);
  });

  // Un 401 al iniciar sesión son credenciales incorrectas, no una sesión caída
  it('ante un 401 al iniciar sesión no navega', async () => {
    const respuesta = new Promise((_, rechazar) => {
      http.post('/api/auth/inicio-sesion', {}).subscribe({ error: rechazar });
    });

    servidor.expectOne('/api/auth/inicio-sesion').flush(
      {
        type: 'about:blank',
        title: 'Unauthorized',
        status: 401,
        detail: 'Correo o contraseña incorrectos.',
        codigo: 'CREDENCIALES_INCORRECTAS',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(respuesta).rejects.toBeDefined();
    expect(navegaciones).toEqual([]);
  });
});
