import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AppComponent } from './app.component';
import { MARCA } from './core/marca';
import { Rol } from './core/modelos/usuario';
import { Sesion } from './core/modelos/sesion';
import { SesionService } from './core/sesion/sesion.service';

@Component({ template: '' })
class PantallaVacia {}

function sesionVigente(rol: Rol = 'CLIENTE'): Sesion {
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

describe('AppComponent', () => {
  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([
          { path: '', component: PantallaVacia },
          { path: 'mis-datos', component: PantallaVacia },
          { path: 'inicio-sesion', component: PantallaVacia },
          { path: 'registro', component: PantallaVacia },
          { path: 'usuarios', component: PantallaVacia },
        ]),
      ],
    }).compileComponents();
  });

  function crear() {
    const fijo = TestBed.createComponent(AppComponent);
    fijo.detectChanges();
    return fijo;
  }

  it('se crea', () => {
    expect(crear().componentInstance).toBeTruthy();
  });

  it('muestra la marca en el encabezado', () => {
    const encabezado: HTMLElement = crear().nativeElement.querySelector('header');
    expect(encabezado.textContent).toContain(MARCA.nombre);
  });

  // La aplicación se presenta como el producto de una empresa (decisiones.md → Sistema de diseño)
  it('no menciona la universidad ni el origen académico', () => {
    const texto: string = crear().nativeElement.textContent.toLowerCase();
    expect(texto).not.toContain('universidad');
    expect(texto).not.toContain('uagrm');
  });

  // decisiones.md → Sistema de diseño: oscuro por defecto, claro a pedido
  describe('botón del tema', () => {
    afterEach(() => delete document.documentElement.dataset['tema']);

    function botonDelTema(fijo: ReturnType<typeof crear>): HTMLButtonElement {
      return fijo.nativeElement.querySelector('button.tema');
    }

    it('está en el encabezado, con o sin sesión, y ofrece el modo claro', () => {
      const boton = botonDelTema(crear());

      expect(boton).toBeTruthy();
      expect(boton.getAttribute('aria-label')).toBe('Cambiar a modo claro');
    });

    it('al pulsarlo pasa a claro y ofrece volver al oscuro', async () => {
      const fijo = crear();

      botonDelTema(fijo).click();
      fijo.detectChanges();
      await fijo.whenStable();

      expect(document.documentElement.dataset['tema']).toBe('claro');
      expect(botonDelTema(fijo).getAttribute('aria-label')).toBe('Cambiar a modo oscuro');
    });
  });

  it('deja lugar para las pantallas', () => {
    expect(crear().nativeElement.querySelector('router-outlet')).toBeTruthy();
  });

  it('sin sesión no ofrece cerrarla', () => {
    expect(crear().nativeElement.querySelector('button.salir')).toBeNull();
  });

  it('con sesión muestra el nombre y permite cerrarla', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente());

    const fijo = crear();

    expect(fijo.nativeElement.querySelector('header').textContent).toContain('Ana Rojas');
    expect(fijo.nativeElement.querySelector('button.salir').textContent?.trim()).toBe('Cerrar sesión');
  });

  // HU-02 RF-11
  it('«Cerrar sesión» la cierra', () => {
    const sesion = TestBed.inject(SesionService);
    sesion.iniciar(sesionVigente());
    const fijo = crear();

    fijo.nativeElement.querySelector('button.salir').click();
    fijo.detectChanges();

    expect(sesion.autenticado()).toBe(false);
    expect(fijo.nativeElement.querySelector('button.salir')).toBeNull();
  });

  // En un celular un nombre largo se recorta con puntos suspensivos: el
  // nombre completo sigue disponible al pasar el puntero
  it('conserva el nombre completo aunque se recorte', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente());

    const usuario: HTMLElement = crear().nativeElement.querySelector('.usuario');

    expect(usuario.getAttribute('title')).toBe('Ana Rojas');
  });

  function enlacesDelMenu(fijo: ReturnType<typeof crear>): HTMLAnchorElement[] {
    return Array.from(fijo.nativeElement.querySelectorAll('nav a'));
  }

  function nombres(enlaces: HTMLAnchorElement[]): string[] {
    return enlaces.map((enlace) => enlace.textContent?.trim() ?? '');
  }

  async function en(url: string, rol: Rol = 'ADMINISTRADOR') {
    TestBed.inject(SesionService).iniciar(sesionVigente(rol));
    const fijo = crear();
    await TestBed.inject(Router).navigateByUrl(url);
    await fijo.whenStable();
    fijo.detectChanges();
    return fijo;
  }

  describe('navegación entre módulos', () => {
    // Sin sesión, el encabezado invita a entrar
    it('sin sesión muestra «Inicio» y ofrece iniciar sesión y crear cuenta', async () => {
      const fijo = crear();
      await TestBed.inject(Router).navigateByUrl('/');
      await fijo.whenStable();
      fijo.detectChanges();

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio']);
      expect(fijo.nativeElement.querySelector('a[href="/inicio-sesion"]')?.textContent?.trim()).toBe(
        'Iniciar sesión',
      );
      expect(fijo.nativeElement.querySelector('a[href="/registro"]')?.textContent?.trim()).toBe('Crear cuenta');
      expect(fijo.nativeElement.querySelector('a[href="/mis-datos"]')).toBeNull();
    });

    it('con sesión ya no ofrece iniciar sesión ni crear cuenta', async () => {
      const fijo = await en('/', 'CLIENTE');

      expect(fijo.nativeElement.querySelector('a[href="/inicio-sesion"]')).toBeNull();
      expect(fijo.nativeElement.querySelector('a[href="/registro"]')).toBeNull();
    });

    // Pedido en la prueba manual: una casa explícita para volver al inicio
    it('un cliente ve solo «Inicio», con el ícono de la casa', async () => {
      const fijo = await en('/', 'CLIENTE');

      const enlaces = enlacesDelMenu(fijo);
      expect(nombres(enlaces)).toEqual(['Inicio']);
      expect(enlaces[0].getAttribute('href')).toBe('/');
      expect(enlaces[0].querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    });

    it('un administrador ve Inicio y Usuarios', async () => {
      const fijo = await en('/');

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio', 'Usuarios']);
      expect(enlacesDelMenu(fijo)[1].getAttribute('href')).toBe('/usuarios');
    });

    it('un empleado ve solo «Inicio»', async () => {
      const fijo = await en('/', 'EMPLEADO');

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio']);
    });

    it('marca el módulo actual', async () => {
      const fijo = await en('/');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Inicio']);
    });

    it('en Usuarios queda marcado ese módulo y no Inicio', async () => {
      const fijo = await en('/usuarios');

      const actuales = enlacesDelMenu(fijo).filter((enlace) => enlace.getAttribute('aria-current') === 'page');
      expect(nombres(actuales)).toEqual(['Usuarios']);
    });
  });

  describe('perfil', () => {
    it('el nombre lleva a Mis datos, con el ícono de una persona', async () => {
      const fijo = await en('/', 'CLIENTE');

      const perfil: HTMLAnchorElement = fijo.nativeElement.querySelector('a.perfil');
      expect(perfil.getAttribute('href')).toBe('/mis-datos');
      expect(perfil.textContent).toContain('Ana Rojas');
      expect(perfil.getAttribute('aria-label')).toBe('Mis datos de Ana Rojas');
      expect(perfil.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    });

    it('en Mis datos queda marcado', async () => {
      const fijo = await en('/mis-datos', 'CLIENTE');

      expect(fijo.nativeElement.querySelector('a.perfil').getAttribute('aria-current')).toBe('page');
    });
  });
});
