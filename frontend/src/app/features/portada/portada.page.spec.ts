import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CatalogoService } from '../../core/api/catalogo.service';
import { Portada, ProductoEnTienda } from '../../core/modelos/catalogo';
import { PortadaPage } from './portada.page';

function producto(idProducto: number, nombre: string): ProductoEnTienda {
  return { idProducto, nombre, marca: null, precio: 10, unidad: 'UNIDAD', imagenPrincipal: null, disponible: true, conModelo: false };
}

// Como responde GET /api/productos/portada (CatalogoIntegracionTest)
const PORTADA: Portada = {
  destacados: [producto(1, 'Taladro percutor')],
  recientes: [producto(2, 'Pala'), producto(1, 'Taladro percutor')],
  categorias: [
    { idCategoria: 3, nombre: 'Herramientas', productos: [producto(1, 'Taladro percutor')] },
    { idCategoria: 4, nombre: 'Jardín', productos: [producto(2, 'Pala')] },
  ],
};

class RecursoFalso<T> {
  readonly value: ReturnType<typeof signal<T | undefined>>;
  readonly isLoading = signal(false);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  constructor(valor: T | undefined) {
    this.value = signal<T | undefined>(valor);
  }
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

// HU-09: la portada de la tienda
describe('PortadaPage', () => {
  let harness: RouterTestingHarness;
  let recurso: RecursoFalso<Portada>;

  async function abrir(portada: Portada | undefined = PORTADA) {
    TestBed.resetTestingModule();
    recurso = new RecursoFalso<Portada>(portada);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', component: PortadaPage },
          { path: 'catalogo', children: [] },
        ]),
        { provide: CatalogoService, useValue: { portada: () => recurso } },
      ],
    });
    harness = await RouterTestingHarness.create('/');
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  // Mientras sale, la leyenda anterior sigue en el DOM: la que vale es la última
  function leyenda(): string | undefined {
    const leyendas = pantalla().querySelectorAll('.ventana .leyenda');
    return leyendas[leyendas.length - 1]?.textContent?.trim();
  }

  function titulos(): string[] {
    return Array.from(pantalla().querySelectorAll('app-carrusel h2')).map((cada) => cada.textContent?.trim() ?? '');
  }

  // HU-09 RF-1: sin guard, para cualquiera
  it('abre sin sesión', async () => {
    await abrir();

    expect(pantalla().querySelector('app-carrusel')).not.toBeNull();
  });

  // HU-09 RF-3
  it('muestra las secciones en orden', async () => {
    await abrir();

    expect(titulos()).toEqual(['Destacados', 'Recientes', 'Herramientas', 'Jardín']);
    const cinta = Array.from(pantalla().querySelectorAll('.cinta button')).map((cada) => cada.textContent?.trim());
    expect(cinta).toEqual(['Destacados', 'Recientes', 'Herramientas', 'Jardín']);
    expect(leyenda()).toBe('Destacados');
  });

  // HU-09 RF-3, RF-4
  it('sin destacados no muestra la sección', async () => {
    await abrir({ ...PORTADA, destacados: [] });

    expect(titulos()).toEqual(['Recientes', 'Herramientas', 'Jardín']);
  });

  // HU-09 RF-3: la cinta cambia la leyenda de la ventana y lleva a la sección
  it('elegir en la cinta cambia la ventana', async () => {
    await abrir();
    Element.prototype.scrollIntoView = vi.fn();
    const jardin = Array.from(pantalla().querySelectorAll<HTMLButtonElement>('.cinta button')).find(
      (cada) => cada.textContent?.trim() === 'Jardín',
    );

    jardin?.click();
    harness.detectChanges();
    await harness.fixture.whenStable();

    expect(leyenda()).toBe('Jardín');
    expect(jardin?.getAttribute('aria-current')).toBe('true');
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
  });

  // HU-09 RF-3: «Destacados» no tiene «Ver todo»: el catálogo no filtra por destacados
  it('«Ver todo» lleva al catálogo filtrado', async () => {
    await abrir();

    const enlaces = Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('app-carrusel a.ver-todo')).map((cada) =>
      cada.getAttribute('href'),
    );
    expect(enlaces).toEqual(['/catalogo', '/catalogo?categoria=3', '/catalogo?categoria=4']);
  });

  // HU-09 RF-12
  it('una tienda vacía lo dice', async () => {
    await abrir({ destacados: [], recientes: [], categorias: [] });

    expect(pantalla().querySelector('app-carrusel')).toBeNull();
    expect(pantalla().textContent).toContain('Todavía no hay productos en la tienda');
  });
});
