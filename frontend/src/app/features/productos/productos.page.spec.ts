import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CategoriasService } from '../../core/api/categorias.service';
import { ProductosService } from '../../core/api/productos.service';
import { CategoriaGestion } from '../../core/modelos/categoria';
import { ErrorApi } from '../../core/modelos/error-api';
import { Pagina } from '../../core/modelos/pagina';
import { FiltrosDeProductos, Producto, ProductoResumen } from '../../core/modelos/producto';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { ProductosPage } from './productos.page';

// Como responde GET /api/gestion/productos (ProductosIntegracionTest)
const TALADRO: ProductoResumen = {
  idProducto: 7,
  codigo: 'HER-0007',
  nombre: 'Taladro percutor 750 W',
  marca: 'Bosch',
  categoria: 'Herramientas eléctricas',
  precio: 420,
  unidad: 'UNIDAD',
  activo: true,
  imagenPrincipal: 'a.png',
  conModelo: true,
};

const MANGUERA: ProductoResumen = {
  idProducto: 8,
  codigo: 'JAR-0008',
  nombre: 'Manguera',
  marca: null,
  categoria: 'Jardín',
  precio: 1234.5,
  unidad: 'METRO',
  activo: false,
  imagenPrincipal: null,
  conModelo: false,
};

const JARDIN: CategoriaGestion = {
  idCategoria: 4,
  nombre: 'Jardín',
  descripcion: null,
  tieneImagen: false,
  visible: true,
  productos: 1,
};

function pagina(contenido: ProductoResumen[], cambios: Partial<Pagina<ProductoResumen>> = {}) {
  return { contenido, pagina: 0, tamano: 20, totalElementos: 21, totalPaginas: 2, ...cambios };
}

// Lo que la pantalla usa de un httpResource
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

class ProductosFalso {
  recurso = new RecursoFalso<Pagina<ProductoResumen>>(pagina([TALADRO, MANGUERA]));
  filtros: Signal<FiltrosDeProductos> | null = null;
  respuesta: Promise<Producto> = Promise.resolve({ ...TALADRO, activo: false } as unknown as Producto);
  cambios: Array<{ id: number; activo: boolean }> = [];

  listado(filtros: Signal<FiltrosDeProductos>) {
    this.filtros = filtros;
    return this.recurso;
  }
  cambiarActivo(id: number, activo: boolean) {
    this.cambios.push({ id, activo });
    return this.respuesta;
  }
  urlDeImagen(id: number, imagen: string, ancho?: number) {
    return `/api/productos/${id}/imagenes/${imagen}${ancho === undefined ? '' : `?ancho=${ancho}`}`;
  }
}

class CategoriasFalso {
  gestion() {
    return new RecursoFalso<CategoriaGestion[]>([JARDIN]);
  }
}

// HU-07: la lista de productos de la gestión
describe('ProductosPage', () => {
  let harness: RouterTestingHarness;
  let productos: ProductosFalso;

  async function abrir(url = '/productos', configurar: (falso: ProductosFalso) => void = () => {}) {
    TestBed.resetTestingModule();
    productos = new ProductosFalso();
    configurar(productos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'productos', component: ProductosPage }]),
        { provide: ProductosService, useValue: productos },
        { provide: CategoriasService, useValue: new CategoriasFalso() },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ProductosPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  }

  function url(): string {
    return TestBed.inject(Router).url;
  }

  function boton(nombre: string): HTMLButtonElement {
    const encontrado = Array.from(pantalla().querySelectorAll('button')).find(
      (cada) => cada.getAttribute('aria-label') === nombre || cada.textContent?.trim() === nombre,
    );
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${nombre}»`);
    }
    return encontrado;
  }

  async function elegir(id: string, valor: string) {
    const selector: HTMLSelectElement = pantalla().querySelector('#' + id)!;
    selector.value = valor;
    selector.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  // HU-07 RF-10: código, nombre, categoría, precio con formato, estado e imagen principal
  it('muestra los productos de la página', async () => {
    await abrir();

    expect(texto()).toContain('HER-0007');
    expect(texto()).toContain('Taladro percutor 750 W');
    expect(texto()).toContain('Bosch');
    expect(texto()).toContain('Herramientas eléctricas');
    expect(texto()).toContain('Bs 1.234,50 / metro');
    expect(texto()).toContain('Inactivo');
    const imagen: HTMLImageElement = pantalla().querySelector('td.miniatura img')!;
    expect(imagen.getAttribute('src')).toBe('/api/productos/7/imagenes/a.png?ancho=400');
    expect(pantalla().querySelectorAll('td.miniatura img')).toHaveLength(1);
    expect(texto()).toContain('Página 1 de 2 · 21 productos');
  });

  // HU-15 RF-12: la marca «3D» solo en los que tienen modelo, con un nombre para el lector de pantalla
  it('marca con 3D los que tienen modelo', async () => {
    await abrir();

    const marcas = pantalla().querySelectorAll('.tres-d');
    expect(marcas).toHaveLength(1);
    expect(marcas[0].closest('tr')?.textContent).toContain('Taladro percutor 750 W');
    expect(marcas[0].getAttribute('aria-label')).toBe('Tiene modelo 3D');
  });

  // HU-07 RF-10: los filtros de la URL llegan al servicio; lo inválido se descarta
  it('lee los filtros de la URL', async () => {
    await abrir('/productos?categoria=4&activo=false&texto=bosch&pagina=1');
    expect(productos.filtros!()).toEqual({ categoria: 4, activo: false, texto: 'bosch', pagina: 1 });
    expect((pantalla().querySelector('#filtro-categoria') as HTMLSelectElement).value).toBe('4');

    await abrir('/productos?categoria=abc&activo=quizas&pagina=-2');
    expect(productos.filtros!()).toEqual({ categoria: null, activo: null, texto: '', pagina: 0 });
  });

  // HU-07 RF-10: elegir un filtro vuelve a la primera página
  it('filtra por categoría y por estado desde la primera página', async () => {
    await abrir('/productos?pagina=1');

    await elegir('filtro-categoria', '4');
    expect(url()).toBe('/productos?categoria=4');

    await elegir('filtro-estado', 'false');
    expect(url()).toBe('/productos?categoria=4&activo=false');
  });

  // HU-07 RF-10
  it('busca después de una pausa al escribir', async () => {
    vi.useFakeTimers();
    try {
      await abrir();
      const entrada: HTMLInputElement = pantalla().querySelector('#filtro-texto')!;
      entrada.value = 'bosch';
      entrada.dispatchEvent(new Event('input'));
      expect(url()).toBe('/productos');

      await vi.advanceTimersByTimeAsync(300);
      expect(url()).toBe('/productos?texto=bosch');
    } finally {
      vi.useRealTimers();
    }
  });

  it('pasa de página', async () => {
    await abrir();

    boton('Siguiente').click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/productos?pagina=1');
  });

  // HU-07 RF-9: desactivar pregunta antes
  it('desactiva después de confirmar y actualiza la fila', async () => {
    await abrir();
    const dialogo = harness.fixture.debugElement.query((nodo) => nodo.componentInstance instanceof DialogoConfirmacion)
      .componentInstance as DialogoConfirmacion;
    vi.spyOn(dialogo, 'abrir').mockResolvedValue(true);

    boton('Desactivar Taladro percutor 750 W').click();
    await harness.fixture.whenStable();

    expect(productos.cambios).toEqual([{ id: 7, activo: false }]);
    expect(texto()).toContain('«Taladro percutor 750 W» quedó inactivo.');
    expect(boton('Activar Taladro percutor 750 W')).toBeTruthy();
  });

  // HU-07 RF-9, RF-10: con el filtro «Activos», el desactivado sale de la lista
  it('con el filtro de estado vuelve a pedir la lista', async () => {
    await abrir('/productos?activo=true');
    const dialogo = harness.fixture.debugElement.query((nodo) => nodo.componentInstance instanceof DialogoConfirmacion)
      .componentInstance as DialogoConfirmacion;
    vi.spyOn(dialogo, 'abrir').mockResolvedValue(true);

    boton('Desactivar Taladro percutor 750 W').click();
    await harness.fixture.whenStable();

    expect(productos.recurso.reload).toHaveBeenCalled();
  });

  // HU-07 RF-9: activar no pregunta
  it('activa sin preguntar', async () => {
    await abrir('/productos', (falso) => {
      falso.respuesta = Promise.resolve({ ...MANGUERA, activo: true } as unknown as Producto);
    });

    boton('Activar Manguera').click();
    await harness.fixture.whenStable();

    expect(productos.cambios).toEqual([{ id: 8, activo: true }]);
    expect(texto()).toContain('«Manguera» quedó activo.');
  });

  it('explica un rechazo del backend', async () => {
    await abrir('/productos', (falso) => {
      falso.respuesta = Promise.reject({
        status: 404,
        detail: 'x',
        codigo: 'PRODUCTO_NO_ENCONTRADO',
      } as ErrorApi);
    });

    boton('Activar Manguera').click();
    await harness.fixture.whenStable();

    expect(texto()).toContain('Ese producto ya no existe. Recargue la página.');
  });

  it('dice cuando no hay productos con esos filtros', async () => {
    await abrir('/productos', (falso) => falso.recurso.value.set(pagina([], { totalElementos: 0, totalPaginas: 0 })));

    expect(texto()).toContain('No hay productos con esos filtros.');
  });

  it('enlaza a crear y a editar', async () => {
    await abrir();

    const enlaces = Array.from(pantalla().querySelectorAll('a')).map((enlace) => enlace.getAttribute('href'));
    expect(enlaces).toContain('/productos/nuevo');
    expect(enlaces).toContain('/productos/7');
  });
});
