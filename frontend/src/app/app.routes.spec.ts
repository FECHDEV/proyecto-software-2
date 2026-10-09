import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { Sesion } from './core/modelos/sesion';
import { Rol } from './core/modelos/usuario';
import { SesionService } from './core/sesion/sesion.service';

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
    },
  };
}

// Las rutas reales de la aplicación, con sus guards
describe('rutas', () => {
  async function navegarComo(rol: Rol | null, url: string): Promise<string> {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    if (rol !== null) {
      TestBed.inject(SesionService).iniciar(sesionCon(rol));
    }
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return TestBed.inject(Router).url;
  }

  // Las pantallas que importan @lucide/angular tardan en compilarse la primera vez dentro de
  // Vitest (el paquete trae todos los iconos; en el build solo quedan los usados). Se cargan
  // antes, para que ese costo no cuente en el tiempo de cada prueba
  beforeAll(async () => {
    await import('./features/inicio-sesion/inicio-sesion.page');
  }, 60_000);

  afterEach(() => localStorage.clear());

  // La gestión de usuarios es del administrador: los demás vuelven al inicio (usabilidad; el
  // backend igual responde 403)
  it('la gestión de usuarios es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/usuarios')).toBe('/usuarios');
    expect(await navegarComo('CLIENTE', '/usuarios')).toBe('/');
    expect(await navegarComo('EMPLEADO', '/usuarios')).toBe('/');
  });

  // HU-02 RF-12: con la sesión iniciada no tiene sentido volver a entrar
  it('con sesión, iniciar sesión lleva al inicio', async () => {
    expect(await navegarComo('CLIENTE', '/inicio-sesion')).toBe('/');
  });

  // HU-01 RF-14
  it('con sesión, crear cuenta lleva al inicio', async () => {
    expect(await navegarComo('CLIENTE', '/registro')).toBe('/');
  });

  // HU-02 RF-12
  it('sin sesión, iniciar sesión se muestra', async () => {
    expect(await navegarComo(null, '/inicio-sesion')).toBe('/inicio-sesion');
  });

  it('sin sesión la gestión de usuarios lleva a iniciar sesión', async () => {
    expect(await navegarComo(null, '/usuarios')).toBe('/inicio-sesion?destino=%2Fusuarios');
  });

  // La portada es el panel: pide sesión y, sin ella, vuelve a la portada al entrar
  it('la portada pide sesión', async () => {
    expect(await navegarComo(null, '/')).toBe('/inicio-sesion?destino=%2F');
    expect(await navegarComo('CLIENTE', '/')).toBe('/');
    expect(await navegarComo('EMPLEADO', '/')).toBe('/');
  });

  it('mis datos pide sesión', async () => {
    expect(await navegarComo(null, '/mis-datos')).toBe('/inicio-sesion?destino=%2Fmis-datos');
    expect(await navegarComo('CLIENTE', '/mis-datos')).toBe('/mis-datos');
  });

  it('las pantallas de la cuenta no piden sesión', async () => {
    expect(await navegarComo(null, '/inicio-sesion')).toBe('/inicio-sesion');
    expect(await navegarComo(null, '/registro')).toBe('/registro');
    expect(await navegarComo(null, '/recuperacion')).toBe('/recuperacion');
  });

  it('una ruta desconocida vuelve a la portada', async () => {
    expect(await navegarComo('CLIENTE', '/no-existe')).toBe('/');
  });
});
