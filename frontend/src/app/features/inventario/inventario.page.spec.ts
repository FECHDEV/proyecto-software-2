import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { InventarioService } from '../../core/api/inventario.service';
import { Categoria } from '../../core/modelos/categoria';
import { FiltrosDeInventario, ProductoEnInventario } from '../../core/modelos/inventario';
import { Pagina } from '../../core/modelos/pagina';
import { InventarioPage } from './inventario.page';

// Como responde GET /api/inventario (InventarioIntegracionTest)
const MANGUERA: ProductoEnInventario = {
  idProducto: 8,
  codigo: 'JAR-0008',
  nombre: 'Manguera',
  categoria: 'Jardín',
  unidad: 'METRO',
  activo: true,
  stock: 3.5,
  stockMinimo: 5,
  stockBajo: true,
  comprometido: 0,
};

const AZADA: ProductoEnInventario = {
  idProducto: 9,
  codigo: 'JAR-0009',
  nombre: 'Azada',
  categoria: 'Jardín',
  unidad: 'UNIDAD',
  activo: false,
  stock: 1200,
  stockMinimo: null,
  stockBajo: false,
  comprometido: 0,
};

function pagina(contenido: ProductoEnInventario[], cambios: Partial<Pagina<ProductoEnInventario>> = {}) {
  return { contenido, pagina: 0, tamano: 20, totalElementos: 21, totalPaginas: 2, ...cambios };
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

class InventarioFalso {
  recurso = new RecursoFalso<Pagina<ProductoEnInventario>>(pagina([AZADA, MANGUERA]));
  filtros: Signal<FiltrosDeInventario> | null = null;
  listado(filtros: Signal<FiltrosDeInventario>) {
    this.filtros = filtros;
    return this.recurso;
  }
  categorias() {
    return new RecursoFalso<Categoria[]>([{ idCategoria: 4, nombre: 'Jardín', descripcion: null, tieneImagen: false }]);
  }
}

// HU-08: la lista de inventario
describe('InventarioPage', () => {
  let harness: RouterTestingHarness;
  let inventario: InventarioFalso;

  async function abrir(url = '/inventario', configurar: (falso: InventarioFalso) => void = () => {}) {
    TestBed.resetTestingModule();
    inventario = new InventarioFalso();
    configurar(inventario);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'inventario', component: InventarioPage }]),
        { provide: InventarioService, useValue: inventario },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, InventarioPage);
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

  // 16/10/2026: con stock 0 la marca es «Agotado», no «Stock bajo»
  it('marca como agotado un producto sin stock', async () => {
    await abrir('/inventario', (falso) => falso.recurso.value.set(pagina([{ ...MANGUERA, stock: 0 }])));

    expect(texto()).toContain('Agotado');
    expect(texto()).not.toContain('Stock bajo');
  });

  // HU-08 RF-9, RF-10: código, nombre, categoría, stock con su unidad, mínimo, estado y stock bajo
  it('muestra el stock de cada producto con la marca de stock bajo', async () => {
    await abrir();

    expect(texto()).toContain('JAR-0008');
    expect(texto()).toContain('3,5 metro');
    expect(texto()).toContain('Stock bajo');
    expect(texto()).toContain('1.200 unidad');
    expect(texto()).toContain('Inactivo');
    expect(pantalla().querySelectorAll('.bajo')).toHaveLength(1);
    expect(texto()).toContain('Página 1 de 2 · 21 productos');
    const enlaces = Array.from(pantalla().querySelectorAll('tbody a')).map((enlace) => enlace.getAttribute('href'));
    expect(enlaces).toEqual(['/inventario/9', '/inventario/8']);
  });

  // HU-08 RF-16: la columna de lo comprometido
  it('muestra lo comprometido de cada producto', async () => {
    await abrir('/inventario', (falso) => falso.recurso.value.set(pagina([{ ...MANGUERA, comprometido: 2.5 }])));

    const celda = pantalla().querySelector('td[data-etiqueta="Comprometido"]');
    expect(celda?.textContent?.replace(/\s+/g, ' ').trim()).toBe('2,5 metro');
    expect(pantalla().querySelector('thead')?.textContent).toContain('Comprometido');
  });

  // HU-08 RF-10: los filtros de la URL llegan al servicio; lo inválido se descarta
  it('lee los filtros de la URL', async () => {
    await abrir('/inventario?texto=tala&categoria=4&activo=false&bajo=true&pagina=1');
    expect(inventario.filtros!()).toEqual({ texto: 'tala', categoria: 4, activo: false, soloBajo: true, pagina: 1 });
    expect((pantalla().querySelector('#filtro-bajo') as HTMLInputElement).checked).toBe(true);

    await abrir('/inventario?categoria=x&activo=quizas&bajo=1&pagina=-1');
    expect(inventario.filtros!()).toEqual({ texto: '', categoria: null, activo: null, soloBajo: false, pagina: 0 });
  });

  // HU-08 RF-9, RF-10: cada filtro vuelve a la primera página
  it('filtra por categoría, estado y stock bajo', async () => {
    await abrir('/inventario?pagina=1');

    const categoria: HTMLSelectElement = pantalla().querySelector('#filtro-categoria')!;
    categoria.value = '4';
    categoria.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(url()).toBe('/inventario?categoria=4');

    const bajo: HTMLInputElement = pantalla().querySelector('#filtro-bajo')!;
    bajo.click();
    await harness.fixture.whenStable();
    expect(url()).toBe('/inventario?categoria=4&bajo=true');

    const estado: HTMLSelectElement = pantalla().querySelector('#filtro-estado')!;
    estado.value = 'true';
    estado.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(url()).toBe('/inventario?categoria=4&activo=true&bajo=true');
  });

  // HU-08 RF-10
  it('busca después de una pausa al escribir', async () => {
    vi.useFakeTimers();
    try {
      await abrir();
      const entrada: HTMLInputElement = pantalla().querySelector('#filtro-texto')!;
      entrada.value = 'tala';
      entrada.dispatchEvent(new Event('input'));
      expect(url()).toBe('/inventario');

      await vi.advanceTimersByTimeAsync(300);
      expect(url()).toBe('/inventario?texto=tala');
    } finally {
      vi.useRealTimers();
    }
  });

  it('dice cuando no hay productos con esos filtros', async () => {
    await abrir('/inventario', (falso) => falso.recurso.value.set(pagina([], { totalElementos: 0, totalPaginas: 0 })));

    expect(texto()).toContain('No hay productos con esos filtros.');
  });
});
