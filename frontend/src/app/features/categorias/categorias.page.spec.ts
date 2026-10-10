import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CategoriasService } from '../../core/api/categorias.service';
import { CategoriaGestion, DatosDeCategoria } from '../../core/modelos/categoria';
import { ErrorApi } from '../../core/modelos/error-api';
import { CategoriasPage } from './categorias.page';

// Como responde GET /api/gestion/categorias (CategoriasIntegracionTest)
const JARDIN: CategoriaGestion = {
  idCategoria: 4,
  nombre: 'Jardín',
  descripcion: 'Patio y huerta',
  tieneImagen: true,
  visible: true,
  productos: 0,
};
const PLOMERIA: CategoriaGestion = {
  idCategoria: 5,
  nombre: 'Plomería',
  descripcion: null,
  tieneImagen: false,
  visible: false,
  productos: 0,
};

// Lo que la pantalla usa del httpResource
class RecursoFalso {
  readonly value = signal<CategoriaGestion[] | undefined>([JARDIN, PLOMERIA]);
  readonly isLoading = signal(false);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class CategoriasFalso {
  recurso = new RecursoFalso();
  respuesta: Promise<CategoriaGestion> = Promise.resolve(JARDIN);
  creadas: DatosDeCategoria[] = [];
  editadas: Array<{ id: number; datos: DatosDeCategoria }> = [];
  visibilidades: Array<{ id: number; visible: boolean }> = [];
  imagenes: Array<{ id: number; archivo: File }> = [];
  borradas: number[] = [];

  gestion() {
    return this.recurso;
  }
  crear(datos: DatosDeCategoria) {
    this.creadas.push(datos);
    return this.respuesta;
  }
  editar(id: number, datos: DatosDeCategoria) {
    this.editadas.push({ id, datos });
    return this.respuesta;
  }
  cambiarVisibilidad(id: number, visible: boolean) {
    this.visibilidades.push({ id, visible });
    return this.respuesta;
  }
  cambiarImagen(id: number, archivo: File) {
    this.imagenes.push({ id, archivo });
    return this.respuesta;
  }
  quitarImagen() {
    return this.respuesta;
  }
  async borrar(id: number) {
    this.borradas.push(id);
  }
  urlDeImagen(id: number) {
    return `/api/categorias/${id}/imagen`;
  }
}

function rechaza(codigo: string, status = 409): Promise<CategoriaGestion> {
  return Promise.reject({ status, detail: 'x', codigo } as ErrorApi);
}

// HU-06: la gestión de categorías
describe('CategoriasPage', () => {
  let fijo: ComponentFixture<CategoriasPage>;
  let categorias: CategoriasFalso;

  beforeEach(async () => {
    categorias = new CategoriasFalso();
    await TestBed.configureTestingModule({
      imports: [CategoriasPage],
      providers: [{ provide: CategoriasService, useValue: categorias }],
    }).compileComponents();
    fijo = TestBed.createComponent(CategoriasPage);
    fijo.autoDetectChanges();
    await fijo.whenStable();
  });

  function pantalla(): HTMLElement {
    return fijo.nativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function fila(nombre: string): HTMLElement {
    const filas: HTMLElement[] = Array.from(pantalla().querySelectorAll('.categoria'));
    const encontrada = filas.find((cada) => cada.querySelector('.nombre')?.textContent?.trim() === nombre);
    if (encontrada === undefined) {
      throw new Error(`No hay fila de ${nombre}`);
    }
    return encontrada;
  }

  function botonDeFila(nombre: string, etiqueta: string): HTMLButtonElement {
    const botones: HTMLButtonElement[] = Array.from(fila(nombre).querySelectorAll('button'));
    const encontrado = botones.find((cada) => cada.textContent?.trim() === etiqueta);
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${etiqueta}» en ${nombre}`);
    }
    return encontrado;
  }

  function escribir(id: string, valor: string) {
    const campo = pantalla().querySelector('#' + id) as HTMLInputElement | HTMLTextAreaElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await fijo.whenStable();
  }

  async function guardar() {
    (pantalla().querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await asentar();
  }

  function dialogo(): HTMLDialogElement {
    return pantalla().querySelector('dialog')!;
  }

  // HU-06 RF-12: todas, visibles y ocultas, con su estado y sus productos
  it('lista todas con su estado y sus productos', () => {
    expect(fila('Jardín').textContent).toContain('Visible');
    expect(fila('Plomería').textContent).toContain('Oculta');
    expect(fila('Jardín').textContent).toContain('0 productos');
  });

  // HU-06 RF-6, RF-11: con imagen la muestra; sin imagen, el ícono genérico
  it('muestra la imagen o el ícono genérico', () => {
    expect(fila('Jardín').querySelector('img')?.getAttribute('src')).toContain('/api/categorias/4/imagen');
    expect(fila('Plomería').querySelector('img')).toBeNull();
    expect(fila('Plomería').querySelector('svg')).not.toBeNull();
  });

  // HU-06 RF-2
  it('crea una categoría y vuelve a pedir la lista', async () => {
    escribir('nombre', 'Pintura');
    escribir('descripcion', 'Látex y esmaltes');

    await guardar();

    expect(categorias.creadas).toEqual([{ nombre: 'Pintura', descripcion: 'Látex y esmaltes' }]);
    expect(categorias.recurso.reload).toHaveBeenCalled();
  });

  // HU-06 RF-3
  it('frena un nombre vacío o de más de 60 caracteres', async () => {
    escribir('nombre', '   ');
    await guardar();
    expect(texto()).toContain('Escribe el nombre');

    escribir('nombre', 'a'.repeat(61));
    await guardar();
    expect(texto()).toContain('60 caracteres');
    expect(categorias.creadas).toEqual([]);
  });

  // HU-06 RF-4: el motivo va junto al campo del nombre
  it('avisa un nombre repetido junto al campo', async () => {
    categorias.respuesta = rechaza('CATEGORIA_EXISTENTE');
    escribir('nombre', 'Jardín');

    await guardar();

    expect(pantalla().querySelector('#error-nombre')?.textContent).toContain('Ya existe una categoría con ese nombre.');
  });

  // HU-06 RF-5
  it('edita una categoría con el mismo formulario', async () => {
    botonDeFila('Jardín', 'Editar').click();
    await fijo.whenStable();
    expect((pantalla().querySelector('#nombre') as HTMLInputElement).value).toBe('Jardín');

    escribir('descripcion', 'Patio');
    await guardar();

    expect(categorias.editadas).toEqual([{ id: 4, datos: { nombre: 'Jardín', descripcion: 'Patio' } }]);
  });

  // HU-06 RF-8
  it('oculta y muestra', async () => {
    botonDeFila('Jardín', 'Ocultar').click();
    await asentar();
    botonDeFila('Plomería', 'Mostrar').click();
    await asentar();

    expect(categorias.visibilidades).toEqual([
      { id: 4, visible: false },
      { id: 5, visible: true },
    ]);
  });

  // HU-06 RF-9, RF-14: confirma nombrándola antes de borrar
  it('borrar pide confirmación nombrándola', async () => {
    botonDeFila('Plomería', 'Borrar').click();
    await fijo.whenStable();

    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(dialogo().textContent).toContain('Plomería');
    (dialogo().querySelector('button.principal') as HTMLButtonElement).click();
    await asentar();

    expect(categorias.borradas).toEqual([5]);
  });

  // HU-06 RF-14
  it('cancelar el borrado no borra nada', async () => {
    botonDeFila('Plomería', 'Borrar').click();
    await fijo.whenStable();
    (dialogo().querySelector('button.secundario') as HTMLButtonElement).click();
    await asentar();

    expect(categorias.borradas).toEqual([]);
  });

  // HU-06 RF-6, RF-7: el archivo elegido viaja; si el backend lo rechaza, se dice por qué
  it('sube la imagen elegida y avisa si no es válida', async () => {
    categorias.respuesta = rechaza('IMAGEN_INVALIDA', 400);
    const archivo = new File(['%PDF'], 'foto.jpg', { type: 'image/jpeg' });
    const entrada = fila('Jardín').querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(entrada, 'files', { value: [archivo] });

    entrada.dispatchEvent(new Event('change'));
    await asentar();

    expect(categorias.imagenes).toEqual([{ id: 4, archivo }]);
    expect(texto()).toContain('Ese archivo no es una imagen JPG, PNG o WebP.');
  });

  // HU-06 RF-9: la categoría que se estaba editando ya no existe
  it('borrar la categoría que se edita sale del modo edición', async () => {
    botonDeFila('Plomería', 'Editar').click();
    await fijo.whenStable();
    botonDeFila('Plomería', 'Borrar').click();
    await fijo.whenStable();
    (dialogo().querySelector('button.principal') as HTMLButtonElement).click();
    await asentar();

    expect(texto()).toContain('Nueva categoría');
    expect((pantalla().querySelector('#nombre') as HTMLInputElement).value).toBe('');
  });

  // HU-06 RF-7: una subida rechazada no cambia la imagen que se ve
  it('una subida rechazada no vuelve a pedir las imágenes', async () => {
    const antes = fila('Jardín').querySelector('img')?.getAttribute('src');
    categorias.respuesta = rechaza('IMAGEN_INVALIDA', 400);
    const entrada = fila('Jardín').querySelector('input[type="file"]') as HTMLInputElement;
    Object.defineProperty(entrada, 'files', { value: [new File(['%PDF'], 'foto.jpg')] });

    entrada.dispatchEvent(new Event('change'));
    await asentar();

    expect(fila('Jardín').querySelector('img')?.getAttribute('src')).toBe(antes);
  });

  // HU-06: sin categorías lo dice
  it('sin categorías invita a crear la primera', async () => {
    categorias.recurso.value.set([]);
    await fijo.whenStable();

    expect(texto()).toContain('Todavía no hay categorías');
  });
});
