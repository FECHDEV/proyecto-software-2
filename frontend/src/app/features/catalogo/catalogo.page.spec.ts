import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CatalogoService } from '../../core/api/catalogo.service';
import { CategoriasService } from '../../core/api/categorias.service';
import { Categoria } from '../../core/modelos/categoria';
import { FiltrosDeCatalogo, ProductoEnTienda } from '../../core/modelos/catalogo';
import { Pagina } from '../../core/modelos/pagina';
import { CatalogoPage } from './catalogo.page';

// Como responde GET /api/productos (CatalogoIntegracionTest)
const PALA: ProductoEnTienda = {
  idProducto: 2,
  nombre: 'Pala',
  marca: 'Truper',
  precio: 50,
  unidad: 'UNIDAD',
  imagenPrincipal: null,
  disponible: true,
  conModelo: false,
};
const AZADA: ProductoEnTienda = { ...PALA, idProducto: 3, nombre: 'Azada', marca: null, disponible: false };

function pagina(
  contenido: ProductoEnTienda[],
  cambios: Partial<Pagina<ProductoEnTienda>> = {},
): Pagina<ProductoEnTienda> {
  return { contenido, pagina: 0, tamano: 24, totalElementos: 30, totalPaginas: 2, ...cambios };
}

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

class CatalogoFalso {
  recurso = new RecursoFalso<Pagina<ProductoEnTienda>>(pagina([PALA, AZADA]));
  filtros: Signal<FiltrosDeCatalogo> | null = null;
  listado(filtros: Signal<FiltrosDeCatalogo>) {
    this.filtros = filtros;
    return this.recurso;
  }
  marcas() {
    return new RecursoFalso<string[]>(['Tramontina', 'Truper']);
  }
}

// HU-09: el catálogo con búsqueda, filtros, orden y páginas
describe('CatalogoPage', () => {
  let harness: RouterTestingHarness;
  let catalogo: CatalogoFalso;

  async function abrir(url = '/catalogo', configurar: (falso: CatalogoFalso) => void = () => {}) {
    TestBed.resetTestingModule();
    catalogo = new CatalogoFalso();
    configurar(catalogo);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'catalogo', component: CatalogoPage }]),
        { provide: CatalogoService, useValue: catalogo },
        {
          provide: CategoriasService,
          useValue: {
            catalogo: () =>
              new RecursoFalso<Categoria[]>([{ idCategoria: 4, nombre: 'Jardín', descripcion: null, tieneImagen: false }]),
          },
        },
      ],
    });
    harness = await RouterTestingHarness.create(url);
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function url(): string {
    return TestBed.inject(Router).url;
  }

  function campo<T extends HTMLElement>(id: string): T {
    return pantalla().querySelector(`#${id}`) as T;
  }

  async function elegir(id: string, valor: string): Promise<void> {
    const select = campo<HTMLSelectElement>(id);
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  async function escribirPrecio(id: string, valor: string): Promise<void> {
    const input = campo<HTMLInputElement>(id);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('change'));
    harness.detectChanges();
    await harness.fixture.whenStable();
  }

  // HU-09 RF-11
  it('lee los filtros de la dirección', async () => {
    await abrir('/catalogo?q=pala&categoria=4&marca=Truper&precioMin=10&precioMax=99,5&disponibles=true&orden=NOMBRE&pagina=1');

    expect(catalogo.filtros?.()).toEqual({
      q: 'pala',
      categoria: 4,
      marca: 'Truper',
      precioMin: 10,
      precioMax: 99.5,
      disponibles: true,
      orden: 'NOMBRE',
      pagina: 1,
    });
    expect(campo<HTMLInputElement>('busqueda').value).toBe('pala');
    expect(campo<HTMLSelectElement>('orden').value).toBe('NOMBRE');
    expect(campo<HTMLInputElement>('disponibles').checked).toBe(true);
  });

  // HU-09 RF-13
  it('descarta lo que no entiende', async () => {
    await abrir('/catalogo?categoria=-1&precioMin=abc&precioMax=1,2,3&orden=xyz&pagina=uno&disponibles=quiza');

    expect(catalogo.filtros?.()).toEqual({
      q: '',
      categoria: null,
      marca: null,
      precioMin: null,
      precioMax: null,
      disponibles: false,
      orden: 'RECIENTES',
      pagina: 0,
    });
  });

  // HU-09 RF-8, RF-13
  it('recorta la búsqueda a 100 caracteres', async () => {
    await abrir(`/catalogo?q=${'a'.repeat(150)}`);

    expect(catalogo.filtros?.().q).toHaveLength(100);
    expect(campo<HTMLInputElement>('busqueda').maxLength).toBe(100);
  });

  // HU-09 RF-10
  it('el orden por defecto es «Más recientes»', async () => {
    await abrir();

    expect(campo<HTMLSelectElement>('orden').value).toBe('RECIENTES');
    expect(campo<HTMLSelectElement>('orden').selectedOptions[0].textContent?.trim()).toBe('Más recientes');
  });

  // HU-09 RF-11
  it('cambiar un filtro vuelve a la primera página', async () => {
    await abrir('/catalogo?pagina=1');

    await elegir('categoria', '4');
    expect(url()).toBe('/catalogo?categoria=4');

    await elegir('orden', 'PRECIO_ASC');
    expect(url()).toBe('/catalogo?categoria=4&orden=PRECIO_ASC');
  });

  // HU-09 RF-14
  it('un precio mínimo mayor que el máximo avisa y no se aplica', async () => {
    await abrir('/catalogo?precioMax=100');

    await escribirPrecio('precio-min', '200');

    expect(url()).toBe('/catalogo?precioMax=100');
    expect(pantalla().textContent).toContain('El mínimo no puede ser mayor que el máximo.');
    expect(campo<HTMLInputElement>('precio-min').getAttribute('aria-invalid')).toBe('true');

    await escribirPrecio('precio-min', '20');
    expect(url()).toBe('/catalogo?precioMin=20&precioMax=100');
  });

  // HU-09 RF-7
  it('marca los agotados', async () => {
    await abrir();

    const tarjetas = Array.from(pantalla().querySelectorAll('app-tarjeta-producto'));
    expect(tarjetas).toHaveLength(2);
    expect(tarjetas[1].textContent).toContain('Agotado');
    expect(tarjetas[0].textContent).not.toContain('Agotado');
  });

  // HU-09 RF-11
  it('muestra el total y las páginas', async () => {
    await abrir();

    expect(pantalla().textContent).toContain('30 productos');
    const actual = pantalla().querySelector('.paginas [aria-current="page"]');
    expect(actual?.textContent?.trim()).toBe('1');
  });

  // HU-09 RF-12
  it('sin resultados ofrece quitar los filtros', async () => {
    await abrir('/catalogo?q=xyz&marca=Truper', (falso) =>
      falso.recurso.value.set(pagina([], { totalElementos: 0, totalPaginas: 0 })),
    );

    expect(pantalla().textContent).toContain('No hay productos para esta búsqueda');
    const quitar = Array.from(pantalla().querySelectorAll('button')).find(
      (cada) => cada.textContent?.trim() === 'Quitar filtros',
    );
    quitar?.click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/catalogo');
  });

  // HU-09 RF-11, RF-12
  it('una página de más ofrece volver a la primera', async () => {
    await abrir('/catalogo?pagina=9', (falso) =>
      falso.recurso.value.set(pagina([], { pagina: 9, totalElementos: 30, totalPaginas: 2 })),
    );

    expect(pantalla().textContent).toContain('No hay productos en esta página');
    const volver = Array.from(pantalla().querySelectorAll('button')).find(
      (cada) => cada.textContent?.trim() === 'Volver a la primera página',
    );
    volver?.click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/catalogo');
  });
});
