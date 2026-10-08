import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Sesion } from '../modelos/sesion';
import { SesionService } from '../sesion/sesion.service';
import { tokenInterceptor } from './token.interceptor';

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

describe('tokenInterceptor', () => {
  let http: HttpClient;
  let servidor: HttpTestingController;
  let sesion: SesionService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([tokenInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    servidor = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SesionService);
  });

  afterEach(() => servidor.verify());

  it('agrega el token a las peticiones protegidas', () => {
    sesion.iniciar(sesionVigente());

    http.get('/api/usuarios').subscribe();

    const peticion = servidor.expectOne('/api/usuarios');
    expect(peticion.request.headers.get('Authorization')).toBe('Bearer jwt.de.prueba');
    peticion.flush({});
  });

  // El backend rechaza un token inválido en /api/auth, así que no se envía
  it('no agrega el token al iniciar sesión', () => {
    sesion.iniciar(sesionVigente());

    http.post('/api/auth/inicio-sesion', {}).subscribe();

    const peticion = servidor.expectOne('/api/auth/inicio-sesion');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({});
  });

  it('no agrega nada cuando no hay sesión', () => {
    http.get('/api/usuarios').subscribe();

    const peticion = servidor.expectOne('/api/usuarios');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({});
  });

  it('no toca las peticiones que no van a la API', () => {
    sesion.iniciar(sesionVigente());

    http.get('/assets/config.json').subscribe();

    const peticion = servidor.expectOne('/assets/config.json');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({});
  });
});
