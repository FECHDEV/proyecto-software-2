import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AppComponent } from './app.component';
import { CarritoService } from './core/api/carrito.service';
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
  const lineasEnElCarrito = signal<number | null>(0);

  beforeEach(async () => {
    lineasEnElCarrito.set(0);
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
          { path: 'catalogo', component: PantallaVacia },
          { path: 'panel', component: PantallaVacia },
          { path: 'carrito', component: PantallaVacia },
          { path: 'mis-pedidos', component: PantallaVacia },
        ]),
        { provide: CarritoService, useValue: { cantidadLineas: lineasEnElCarrito } },
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

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio', 'Catálogo']);
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

    // Pedido en la prueba manual: una casa explícita para volver al inicio. HU-09
    // RF-16, RF-17: el cliente no tiene panel
    it('un cliente ve «Inicio» y «Catálogo», con el ícono de la casa', async () => {
      const fijo = await en('/', 'CLIENTE');

      const enlaces = enlacesDelMenu(fijo);
      expect(nombres(enlaces)).toEqual(['Inicio', 'Catálogo']);
      expect(enlaces[0].getAttribute('href')).toBe('/');
      expect(enlaces[0].querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    });

    // HU-06 RF-1, HU-07 RF-1: la gestión de categorías y la de productos también son del administrador
    it('un administrador ve Inicio, Usuarios, Categorías y Productos', async () => {
      const fijo = await en('/');

      expect(nombres(enlacesDelMenu(fijo))).toEqual([
        'Inicio',
        'Catálogo',
        'Panel',
        'Usuarios',
        'Categorías',
        'Productos',
        'Inventario',
      ]);
      expect(enlacesDelMenu(fijo)[3].getAttribute('href')).toBe('/usuarios');
      expect(enlacesDelMenu(fijo)[4].getAttribute('href')).toBe('/categorias');
      expect(enlacesDelMenu(fijo)[5].getAttribute('href')).toBe('/productos');
    });

    // HU-08 RF-1
    it('un empleado ve «Inicio» e «Inventario»', async () => {
      const fijo = await en('/', 'EMPLEADO');

      expect(nombres(enlacesDelMenu(fijo))).toEqual(['Inicio', 'Catálogo', 'Panel', 'Inventario']);
      expect(enlacesDelMenu(fijo)[3].getAttribute('href')).toBe('/inventario');
    });

    // HU-09 RF-16
    it('el panel es solo para el personal', async () => {
      const fijo = await en('/', 'CONTADOR');

      expect(enlacesDelMenu(fijo)[2].getAttribute('href')).toBe('/panel');
    });

    // HU-09 RF-8, RF-17: la búsqueda del encabezado, con o sin sesión
    it('la búsqueda del encabezado lleva al catálogo', async () => {
      const fijo = crear();
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/');
      const busqueda: HTMLInputElement = fijo.nativeElement.querySelector('header input[type="search"]');
      expect(busqueda.labels?.[0]?.textContent?.trim()).toBe('Buscar en la tienda');

      busqueda.value = '  martillo ';
      busqueda.dispatchEvent(new Event('input'));
      fijo.nativeElement.querySelector('header form').dispatchEvent(new Event('submit'));
      await fijo.whenStable();

      expect(router.url).toBe('/catalogo?q=martillo');
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

  // HU-14 RF-6: «Mis pedidos» en el encabezado, solo para el Cliente
  it('Mis pedidos solo para el Cliente', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente('CLIENTE'));
    const enlace = crear().nativeElement.querySelector('header a[href="/mis-pedidos"]');
    expect(enlace?.getAttribute('aria-label')).toBe('Mis pedidos');

    TestBed.inject(SesionService).iniciar(sesionVigente('EMPLEADO'));
    expect(crear().nativeElement.querySelector('header a[href="/mis-pedidos"]')).toBeNull();
  });

  // --- HU-11: el ícono del carrito ---

  function enlaceAlCarrito(fijo: ReturnType<typeof crear>): HTMLAnchorElement | null {
    return fijo.nativeElement.querySelector('header a[href="/carrito"]');
  }

  // HU-11 RF-1
  it('el encabezado muestra cuántos productos tiene el carrito', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente('CLIENTE'));
    lineasEnElCarrito.set(3);
    const fijo = crear();

    const enlace = enlaceAlCarrito(fijo);
    expect(enlace?.getAttribute('aria-label')).toBe('Carrito, 3 productos');
    expect(enlace?.textContent?.trim()).toBe('3');

    lineasEnElCarrito.set(1);
    fijo.detectChanges();
    expect(enlace?.getAttribute('aria-label')).toBe('Carrito, 1 producto');
  });

  // HU-11 RF-11
  it('sin productos el ícono no muestra número', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente('CLIENTE'));
    const fijo = crear();

    const enlace = enlaceAlCarrito(fijo);
    expect(enlace?.getAttribute('aria-label')).toBe('Carrito, sin productos');
    expect(enlace?.textContent?.trim()).toBe('');
  });

  // HU-11 RF-7
  it('el ícono del carrito es solo para el cliente', () => {
    expect(enlaceAlCarrito(crear())).toBeNull();

    TestBed.inject(SesionService).iniciar(sesionVigente('ADMINISTRADOR'));
    expect(enlaceAlCarrito(crear())).toBeNull();
  });

  // Revisión de HU-11 (/code-review): si el carrito no cargó, no dice que está vacío
  it('sin el carrito cargado el ícono no afirma cuántos hay', () => {
    TestBed.inject(SesionService).iniciar(sesionVigente('CLIENTE'));
    lineasEnElCarrito.set(null);
    const fijo = crear();

    const enlace = enlaceAlCarrito(fijo);
    expect(enlace?.getAttribute('aria-label')).toBe('Carrito');
    expect(enlace?.textContent?.trim()).toBe('');
  });
});
