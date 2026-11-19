import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CategoriasService } from '../../core/api/categorias.service';
import { DescuentosService } from '../../core/api/descuentos.service';
import { ProductosService } from '../../core/api/productos.service';
import { CategoriaGestion } from '../../core/modelos/categoria';
import {
  CambioDeDescuento,
  Descuento,
  DescuentoGuardado,
  SolicitudDeDescuento,
} from '../../core/modelos/descuento';
import { ErrorApi } from '../../core/modelos/error-api';
import { Pagina } from '../../core/modelos/pagina';
import { ProductoResumen } from '../../core/modelos/producto';
import { hoyEnBolivia } from '../../core/modelos/reportes';
import { DescuentosPage } from './descuentos.page';

// Las fechas, contadas desde el día de hoy en Bolivia: la pantalla no deja empezar antes de hoy
function enDias(dias: number): string {
  const fecha = new Date(`${hoyEnBolivia()}T00:00:00Z`);
  fecha.setUTCDate(fecha.getUTCDate() + dias);
  return fecha.toISOString().slice(0, 10);
}

// Como responde GET /api/gestion/descuentos (DescuentosIntegracionTest)
const PROGRAMADO: Descuento = {
  idDescuento: 1,
  idProducto: 7,
  producto: 'Rastrillo',
  idCategoria: null,
  categoria: null,
  porcentaje: 15,
  inicio: enDias(2),
  fin: enDias(10),
  estado: 'PROGRAMADO',
};
const VIGENTE: Descuento = {
  idDescuento: 2,
  idProducto: null,
  producto: null,
  idCategoria: 4,
  categoria: 'Jardín',
  porcentaje: 10,
  inicio: enDias(-3),
  fin: enDias(5),
  estado: 'VIGENTE',
};
const VENCIDO: Descuento = {
  ...PROGRAMADO,
  idDescuento: 3,
  producto: 'Manguera',
  inicio: enDias(-20),
  fin: enDias(-10),
  estado: 'VENCIDO',
};

function pagina<T>(contenido: T[]): Pagina<T> {
  return { contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 };
}

class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly isLoading = signal(false);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  constructor(valor?: T) {
    this.value.set(valor);
  }
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

const GUARDADO: DescuentoGuardado = { descuento: PROGRAMADO, bajoElCosto: [], totalBajoElCosto: 0 };

class DescuentosFalso {
  readonly recurso = new RecursoFalso<Pagina<Descuento>>(pagina([PROGRAMADO, VIGENTE, VENCIDO]));
  readonly crear = vi.fn((_solicitud: SolicitudDeDescuento): Promise<DescuentoGuardado> => Promise.resolve(GUARDADO));
  readonly cambiar = vi.fn(
    (_id: number, _cambio: CambioDeDescuento): Promise<DescuentoGuardado> => Promise.resolve(GUARDADO),
  );
  readonly borrar = vi.fn((_id: number): Promise<void> => Promise.resolve());
  readonly terminar = vi.fn((_id: number): Promise<void> => Promise.resolve());
  listar() {
    return this.recurso;
  }
}

const RASTRILLO = { idProducto: 7, codigo: 'JAR-0007', nombre: 'Rastrillo' } as ProductoResumen;

class ProductosFalso {
  readonly encontrados = new RecursoFalso<Pagina<ProductoResumen>>();
  buscado = '';
  buscarActivos(texto: () => string) {
    // Como httpResource: sin texto no hay nada
    return {
      value: () => (texto().trim() === '' ? undefined : this.encontrados.value()),
      hasValue: () => texto().trim() !== '' && this.encontrados.value() !== undefined,
      isLoading: () => false,
    };
  }
}

const JARDIN = { idCategoria: 4, nombre: 'Jardín' } as CategoriaGestion;

// HU-24 RF-1 a RF-7: la gestión de descuentos
describe('DescuentosPage', () => {
  let fijo: ComponentFixture<DescuentosPage>;
  let descuentos: DescuentosFalso;
  let productos: ProductosFalso;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    descuentos = new DescuentosFalso();
    productos = new ProductosFalso();
    productos.encontrados.value.set(pagina([RASTRILLO]));
    await TestBed.configureTestingModule({
      imports: [DescuentosPage],
      providers: [
        { provide: DescuentosService, useValue: descuentos },
        { provide: ProductosService, useValue: productos },
        { provide: CategoriasService, useValue: { gestion: () => new RecursoFalso([JARDIN]) } },
      ],
    }).compileComponents();
    fijo = TestBed.createComponent(DescuentosPage);
    fijo.autoDetectChanges();
    await fijo.whenStable();
  });

  const pantalla = (): HTMLElement => fijo.nativeElement as HTMLElement;
  const texto = (elemento: Element | null = pantalla()): string =>
    (elemento?.textContent ?? '').replace(/\s+/g, ' ');

  function fila(nombre: string): HTMLElement {
    const encontrada = Array.from(pantalla().querySelectorAll<HTMLElement>('.descuento')).find((cada) =>
      cada.querySelector('.objetivo')?.textContent?.includes(nombre),
    );
    if (encontrada === undefined) {
      throw new Error(`No hay fila de ${nombre}`);
    }
    return encontrada;
  }

  function botones(nombre: string): string[] {
    return Array.from(fila(nombre).querySelectorAll('button')).map((cada) => cada.textContent?.trim() ?? '');
  }

  function boton(nombre: string, etiqueta: string): HTMLButtonElement {
    return Array.from(fila(nombre).querySelectorAll('button')).find((cada) => cada.textContent?.trim() === etiqueta)!;
  }

  function escribir(id: string, valor: string): void {
    const campo = pantalla().querySelector<HTMLInputElement>('#' + id)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  async function asentar(): Promise<void> {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await fijo.whenStable();
  }

  async function guardar(): Promise<void> {
    pantalla().querySelector('form')!.dispatchEvent(new Event('submit'));
    await asentar();
  }

  async function elegirRastrillo(): Promise<void> {
    escribir('texto-producto', 'rastri');
    Array.from(pantalla().querySelectorAll('button'))
      .find((cada) => cada.textContent?.trim() === 'Buscar')!
      .click();
    await fijo.whenStable();
    pantalla().querySelector<HTMLButtonElement>('.resultado')!.click();
    await fijo.whenStable();
  }

  function dialogo(): HTMLDialogElement {
    return pantalla().querySelector('dialog')!;
  }

  // HU-24 RF-3
  it('RF-3: lista con el estado de cada uno', () => {
    expect(texto(fila('Rastrillo'))).toContain('−15 %');
    expect(texto(fila('Rastrillo'))).toContain('Programado');
    expect(texto(fila('Jardín'))).toContain('Categoría «Jardín»');
    expect(texto(fila('Jardín'))).toContain('Vigente');
    expect(texto(fila('Manguera'))).toContain('Vencido');
  });

  // HU-24 RF-1
  it('RF-1: crea el descuento de un producto', async () => {
    await elegirRastrillo();
    escribir('porcentaje', '15');
    escribir('inicio', enDias(1));
    escribir('fin', enDias(8));
    await guardar();

    expect(descuentos.crear).toHaveBeenCalledWith({
      idProducto: 7,
      idCategoria: null,
      porcentaje: 15,
      inicio: enDias(1),
      fin: enDias(8),
    });
    expect(texto()).toContain('Descuento guardado.');
    expect(descuentos.recurso.reload).toHaveBeenCalled();
  });

  // HU-24 RF-1
  it('RF-1: crea el descuento de una categoría', async () => {
    pantalla().querySelectorAll<HTMLInputElement>('input[type=radio]')[1].click();
    await fijo.whenStable();
    const categoria = pantalla().querySelector<HTMLSelectElement>('#categoria')!;
    categoria.value = '4';
    categoria.dispatchEvent(new Event('change'));
    escribir('porcentaje', '10');
    escribir('fin', enDias(3));
    await guardar();

    expect(descuentos.crear).toHaveBeenCalledWith({
      idProducto: null,
      idCategoria: 4,
      porcentaje: 10,
      inicio: enDias(0),
      fin: enDias(3),
    });
  });

  // HU-24 RF-1
  it('RF-1: valida el porcentaje y las fechas antes de enviar', async () => {
    await elegirRastrillo();
    escribir('porcentaje', '91');
    escribir('inicio', enDias(-1));
    escribir('fin', enDias(-2));
    await guardar();

    expect(descuentos.crear).not.toHaveBeenCalled();
    expect(texto()).toContain('El porcentaje es un número entero de 1 a 90.');
    expect(texto()).toContain('La fecha de inicio no puede ser anterior a hoy.');
    expect(texto()).toContain('La fecha de fin no puede ser anterior a la de inicio.');
    expect(document.activeElement?.id).toBe('porcentaje');

    escribir('porcentaje', '12,5');
    escribir('inicio', enDias(0));
    escribir('fin', enDias(367));
    await guardar();
    expect(texto()).toContain('El porcentaje es un número entero de 1 a 90.');
    expect(texto()).toContain('Un descuento dura como máximo 366 días.');
    expect(descuentos.crear).not.toHaveBeenCalled();
  });

  // HU-24 RF-1: sin producto no se envía
  it('RF-1: pide el producto', async () => {
    escribir('porcentaje', '15');
    escribir('fin', enDias(3));
    await guardar();

    expect(descuentos.crear).not.toHaveBeenCalled();
    expect(texto()).toContain('Busque y elija el producto.');
    expect(document.activeElement?.id).toBe('texto-producto');

    // Revisión de interfaz: al escribir para buscarlo, el error se va
    escribir('texto-producto', 'ras');
    await fijo.whenStable();
    expect(texto()).not.toContain('Busque y elija el producto.');
  });

  // HU-24 RF-2
  it('RF-2: muestra con cuál se cruza', async () => {
    descuentos.crear.mockRejectedValueOnce({
      status: 409,
      detail: 'x',
      codigo: 'DESCUENTO_SOLAPADO',
      porcentaje: 10,
      inicio: '2026-11-20',
      fin: '2026-11-30',
    } as ErrorApi);
    await elegirRastrillo();
    escribir('porcentaje', '15');
    escribir('fin', enDias(3));
    await guardar();

    expect(texto()).toContain('Se cruza con el descuento del 10 % del 20/11/2026 al 30/11/2026.');
  });

  // HU-24 RF-4
  it('RF-4: Editar y Borrar solo en los programados', async () => {
    expect(botones('Rastrillo')).toEqual(['Editar', 'Borrar']);
    expect(botones('Jardín')).toEqual(['Terminar']);
    expect(botones('Manguera')).toEqual([]);

    boton('Rastrillo', 'Editar').click();
    await fijo.whenStable();
    expect(pantalla().querySelector('h2')?.textContent).toContain('Editar el descuento de Rastrillo');
    expect(pantalla().querySelector('input[type=radio]')).toBeNull();
    escribir('porcentaje', '20');
    await guardar();

    expect(descuentos.cambiar).toHaveBeenCalledWith(1, { porcentaje: 20, inicio: PROGRAMADO.inicio, fin: PROGRAMADO.fin });
  });

  // HU-24 RF-3 (/code-review): si la página quedó vacía (se borró su último descuento), vuelve a la última que hay
  it('RF-3: una página que quedó vacía vuelve a la anterior', async () => {
    const componente = fijo.componentInstance as unknown as { pagina: { (): number; set(valor: number): void } };
    componente.pagina.set(1);
    descuentos.recurso.value.set({ ...pagina<Descuento>([]), pagina: 1, totalPaginas: 1 });
    await fijo.whenStable();

    expect(componente.pagina()).toBe(0);
  });

  // HU-24 RF-4 (revisión de interfaz): «Cancelar» devuelve el foco al «Editar» de esa fila, no al body
  it('RF-4: cancelar la edición vuelve el foco a «Editar»', async () => {
    boton('Rastrillo', 'Editar').click();
    await fijo.whenStable();
    [...pantalla().querySelectorAll<HTMLButtonElement>('form button')].find((b) => b.textContent?.trim() === 'Cancelar')!.click();
    await fijo.whenStable();

    expect(pantalla().querySelector('h2')?.textContent).toContain('Nuevo descuento');
    expect(document.activeElement).toBe(boton('Rastrillo', 'Editar'));
  });

  // HU-24 RF-4 (revisión de interfaz): en una frase, «de la categoría», en minúscula
  it('RF-4: el descuento de una categoría se nombra en minúscula dentro de la frase', () => {
    expect(boton('Jardín', 'Terminar').getAttribute('aria-label')).toBe('Terminar el descuento de la categoría «Jardín»');
  });

  // HU-24 RF-4
  it('RF-4: borrar pide confirmación', async () => {
    boton('Rastrillo', 'Borrar').click();
    await fijo.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(dialogo().textContent).toContain('Rastrillo');
    dialogo().querySelector<HTMLButtonElement>('button.principal')!.click();
    await asentar();

    expect(descuentos.borrar).toHaveBeenCalledWith(1);
  });

  // HU-24 RF-5
  it('RF-5: Terminar solo en los vigentes y pide confirmación', async () => {
    boton('Jardín', 'Terminar').click();
    await fijo.whenStable();
    expect(dialogo().textContent).toContain('vuelve al precio normal');
    dialogo().querySelector<HTMLButtonElement>('button.secundario')!.click();
    await asentar();
    expect(descuentos.terminar).not.toHaveBeenCalled();

    boton('Jardín', 'Terminar').click();
    await fijo.whenStable();
    dialogo().querySelector<HTMLButtonElement>('button.principal')!.click();
    await asentar();
    expect(descuentos.terminar).toHaveBeenCalledWith(2);
  });

  // HU-24 RF-6
  it('RF-6: avisa los productos bajo el costo', async () => {
    descuentos.crear.mockResolvedValueOnce({ descuento: PROGRAMADO, bajoElCosto: ['Taladro'], totalBajoElCosto: 12 });
    await elegirRastrillo();
    escribir('porcentaje', '20');
    escribir('fin', enDias(3));
    await guardar();

    const aviso = pantalla().querySelector('.bajo-el-costo')!;
    expect(texto(aviso)).toContain('por debajo de su costo');
    expect(texto(aviso)).toContain('Taladro');
    expect(texto(aviso)).toContain('Y 11 más.');
  });

  it('un error de red muestra «Reintentar»', async () => {
    descuentos.recurso.value.set(undefined);
    descuentos.recurso.error.set(new Error('red'));
    // La página anterior se olvida al fallar (ultimoValor la conserva solo mientras carga)
    fijo.destroy();
    fijo = TestBed.createComponent(DescuentosPage);
    fijo.autoDetectChanges();
    await fijo.whenStable();

    expect(texto()).toContain('No pudimos cargar los descuentos.');
    const reintentar = Array.from(pantalla().querySelectorAll('button')).find(
      (cada) => cada.textContent?.trim() === 'Reintentar',
    )!;
    reintentar.click();
    expect(descuentos.recurso.reload).toHaveBeenCalled();
  });
});
