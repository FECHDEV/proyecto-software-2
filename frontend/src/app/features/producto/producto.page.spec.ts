import { Component, Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CategoriasService } from '../../core/api/categorias.service';
import { ProductosService } from '../../core/api/productos.service';
import { CategoriaGestion } from '../../core/modelos/categoria';
import { ErrorApi } from '../../core/modelos/error-api';
import { DatosDeProducto, Producto } from '../../core/modelos/producto';
import { DialogoConfirmacion } from '../../shared/dialogo-confirmacion/dialogo-confirmacion';
import { ProductoPage } from './producto.page';

// Como responde GET /api/gestion/productos/7 (ProductosIntegracionTest)
const TALADRO: Producto = {
  idProducto: 7,
  codigo: 'HER-0007',
  nombre: 'Taladro percutor 750 W',
  marca: 'Bosch',
  descripcion: null,
  precio: 420,
  costo: 300.5,
  unidad: 'UNIDAD',
  idCategoria: 3,
  categoria: 'Herramientas eléctricas',
  activo: true,
  fechaAlta: '2026-10-10T09:00:00',
  ficha: [{ nombre: 'Potencia', valor: '750 W' }],
  imagenes: ['a.png', 'b.png'],
  destacado: false,
};

const CATEGORIAS: CategoriaGestion[] = [
  { idCategoria: 3, nombre: 'Herramientas eléctricas', descripcion: null, tieneImagen: false, visible: true, productos: 1 },
  { idCategoria: 4, nombre: 'Jardín', descripcion: null, tieneImagen: false, visible: false, productos: 0 },
];

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
  recurso = new RecursoFalso<Producto>(undefined);
  id: Signal<number | null> | null = null;
  respuesta: Promise<Producto> = Promise.resolve(TALADRO);
  creados: DatosDeProducto[] = [];
  editados: Array<{ id: number; datos: DatosDeProducto }> = [];
  subidas: Array<{ id: number; archivo: File }> = [];
  ordenes: Array<{ id: number; imagenes: readonly string[] }> = [];
  quitadas: string[] = [];

  producto(id: Signal<number | null>) {
    this.id = id;
    return this.recurso;
  }
  crear(datos: DatosDeProducto) {
    this.creados.push(datos);
    return this.respuesta;
  }
  editar(id: number, datos: DatosDeProducto) {
    this.editados.push({ id, datos });
    return this.respuesta;
  }
  agregarImagen(id: number, archivo: File) {
    this.subidas.push({ id, archivo });
    return this.respuesta;
  }
  reordenar(id: number, imagenes: readonly string[]) {
    this.ordenes.push({ id, imagenes });
    return this.respuesta;
  }
  quitarImagen(_id: number, imagen: string) {
    this.quitadas.push(imagen);
    return this.respuesta;
  }
  urlDeImagen(id: number, imagen: string) {
    return `/api/productos/${id}/imagenes/${imagen}`;
  }
}

class CategoriasFalso {
  readonly recurso = new RecursoFalso<CategoriaGestion[]>(CATEGORIAS);
  gestion() {
    return this.recurso;
  }
}

@Component({ template: '' })
class ListaFalsa {}

function rechaza(codigo: string, status = 409, errores?: ErrorApi['errores']): Promise<Producto> {
  return Promise.reject({ status, detail: 'x', codigo, errores } as ErrorApi);
}

// HU-07: alta y edición de un producto
describe('ProductoPage', () => {
  let harness: RouterTestingHarness;
  let productos: ProductosFalso;
  let categorias: CategoriasFalso;

  async function abrir(url: string, configurar: (falso: ProductosFalso) => void = () => {}) {
    TestBed.resetTestingModule();
    productos = new ProductosFalso();
    categorias = new CategoriasFalso();
    configurar(productos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'productos', component: ListaFalsa },
          { path: 'productos/:id', component: ProductoPage },
        ]),
        { provide: ProductosService, useValue: productos },
        { provide: CategoriasService, useValue: categorias },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, ProductoPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  }

  function campo<T extends HTMLElement = HTMLInputElement>(id: string): T {
    const encontrado = pantalla().querySelector<T>('#' + id);
    if (encontrado === null) {
      throw new Error(`No hay campo #${id}`);
    }
    return encontrado;
  }

  async function escribir(id: string, valor: string) {
    const entrada = campo<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>(id);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    entrada.dispatchEvent(new Event('change'));
    entrada.dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
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

  // El envío termina después de la validación de Signal Forms: se espera una vuelta más
  async function pulsar(nombre: string) {
    boton(nombre).click();
    await harness.fixture.whenStable();
    await new Promise((listo) => setTimeout(listo));
    await harness.fixture.whenStable();
  }

  function dialogo(): DialogoConfirmacion {
    return harness.fixture.debugElement.query((nodo) => nodo.componentInstance instanceof DialogoConfirmacion)
      .componentInstance as DialogoConfirmacion;
  }

  async function llenarLoObligatorio() {
    await escribir('nombre', 'Taladro percutor 750 W');
    await escribir('idCategoria', '3');
    await escribir('precio', '420,5');
  }

  // HU-07 RF-2: un producto nuevo empieza vacío y sin fotos
  it('ofrece el formulario vacío para un producto nuevo', async () => {
    await abrir('/productos/nuevo');

    expect(texto()).toContain('Nuevo producto');
    expect(campo('nombre').value).toBe('');
    expect(campo<HTMLSelectElement>('unidad').value).toBe('UNIDAD');
    expect(texto()).toContain('Cree el producto para subir sus fotos.');
    expect(productos.id!()).toBeNull();
    // Las ocultas también se pueden elegir, y se distinguen
    expect(texto()).toContain('Jardín (oculta)');
  });

  // HU-07 RF-3: nombre, categoría y precio son obligatorios
  it('marca lo que falta y no envía', async () => {
    await abrir('/productos/nuevo');

    await pulsar('Crear producto');

    expect(texto()).toContain('Escriba el nombre del producto.');
    expect(texto()).toContain('Elija una categoría.');
    expect(texto()).toContain('Escriba el precio.');
    expect(productos.creados).toEqual([]);
    expect(document.activeElement?.id).toBe('nombre');
  });

  // HU-07 RF-3: 0 Bs o tres decimales no se guardan
  it('rechaza un precio de cero o con tres decimales', async () => {
    await abrir('/productos/nuevo');
    await llenarLoObligatorio();

    await escribir('precio', '0');
    await pulsar('Crear producto');
    expect(texto()).toContain('El precio tiene que ser mayor que 0.');

    await escribir('precio', '12,345');
    await pulsar('Crear producto');
    expect(texto()).toContain('El precio admite hasta dos decimales.');
    expect(productos.creados).toEqual([]);
  });

  // HU-07 RF-2, RF-4: después de crear pasa a su página, con el código, para subir las fotos
  it('crea y pasa a la página del producto con su código', async () => {
    await abrir('/productos/nuevo', (falso) => {
      falso.respuesta = Promise.resolve({ ...TALADRO, imagenes: [] });
    });
    await llenarLoObligatorio();

    await pulsar('Crear producto');

    expect(productos.creados).toEqual([
      {
        nombre: 'Taladro percutor 750 W',
        marca: '',
        descripcion: '',
        idCategoria: '3',
        unidad: 'UNIDAD',
        precio: '420,5',
        costo: '',
        ficha: [],
        destacado: false,
      },
    ]);
    expect(TestBed.inject(Router).url).toBe('/productos/7');
    expect(texto()).toContain('Producto creado con el código HER-0007.');
    expect(texto()).toContain('HER-0007');
    expect(texto()).toContain('Subir foto');
    // Lo escrito sigue en pantalla mientras se vuelve a pedir el producto
    expect(campo('nombre').value).toBe('Taladro percutor 750 W');
  });

  // HU-07 RF-6: los datos de la ficha se agregan y se quitan
  it('agrega y quita datos de la ficha', async () => {
    await abrir('/productos/nuevo');
    await llenarLoObligatorio();

    await pulsar('Agregar dato');
    expect(document.activeElement?.id).toBe('dato-nombre-0');
    await escribir('dato-nombre-0', 'Potencia');
    await escribir('dato-valor-0', '750 W');
    await pulsar('Agregar dato');
    await escribir('dato-nombre-1', 'Peso');
    await pulsar('Quitar el dato 2');
    await pulsar('Crear producto');

    // Signal Forms marca cada fila con una clave propia: se comparan solo los datos
    expect(productos.creados[0].ficha.map(({ nombre, valor }) => ({ nombre, valor }))).toEqual([
      { nombre: 'Potencia', valor: '750 W' },
    ]);
  });

  // HU-07 RF-6: un par a medio llenar y dos datos con el mismo nombre se marcan
  it('marca un dato sin valor y los nombres repetidos', async () => {
    await abrir('/productos/nuevo');
    await llenarLoObligatorio();
    await pulsar('Agregar dato');
    await escribir('dato-nombre-0', 'Potencia');
    await pulsar('Agregar dato');
    await escribir('dato-nombre-1', ' POTÉNCIA ');
    await escribir('dato-valor-1', '800 W');

    await pulsar('Crear producto');

    expect(texto()).toContain('Escriba el valor.');
    expect(texto()).toContain('Hay otro dato con este nombre.');
    expect(productos.creados).toEqual([]);
  });

  // HU-07 RF-6: a lo sumo 15 datos
  it('no deja agregar más de 15 datos', async () => {
    await abrir('/productos/nuevo');
    for (let i = 0; i < 15; i++) {
      await pulsar('Agregar dato');
    }

    expect(boton('Agregar dato').disabled).toBe(true);
  });

  // HU-07 RF-5: edita con los datos cargados; el precio se muestra con coma
  it('carga el producto y guarda los cambios', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));

    expect(productos.id!()).toBe(7);
    expect(campo('nombre').value).toBe('Taladro percutor 750 W');
    expect(campo<HTMLSelectElement>('idCategoria').value).toBe('3');
    expect(campo('precio').value).toBe('420');
    expect(campo('dato-valor-0').value).toBe('750 W');

    await escribir('idCategoria', '4');
    await pulsar('Guardar cambios');

    expect(productos.editados[0].id).toBe(7);
    expect(productos.editados[0].datos.idCategoria).toBe('4');
    expect(texto()).toContain('Cambios guardados.');
  });

  // HU-18 RF-19: el costo es opcional, se carga con coma y viaja con el producto
  it('envía el costo opcional', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));
    expect(campo('costo').value).toBe('300,5');
    expect(texto()).toContain('Solo lo ven el Administrador y el Contador.');

    await escribir('costo', '310');
    await pulsar('Guardar cambios');
    expect(productos.editados[0].datos.costo).toBe('310');

    await abrir('/productos/7', (falso) => falso.recurso.value.set({ ...TALADRO, costo: null }));
    expect(campo('costo').value).toBe('');
  });

  // HU-18 RF-19: las reglas del precio, con el nombre del costo
  it('muestra el error de costo', async () => {
    await abrir('/productos/nuevo');
    await llenarLoObligatorio();

    await escribir('costo', '0');
    await pulsar('Crear producto');

    expect(texto()).toContain('El costo tiene que ser mayor que 0.');
    expect(productos.creados).toEqual([]);
    expect(document.activeElement?.id).toBe('costo');
  });

  // HU-09 RF-4: la casilla «Destacado» viaja con el producto
  it('guarda «Destacado»', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));
    const casilla = campo('destacado');
    expect(casilla.checked).toBe(false);

    casilla.click();
    await harness.fixture.whenStable();
    await pulsar('Guardar cambios');

    expect(productos.editados[0].datos.destacado).toBe(true);
  });

  // HU-07 RF-6: un par vacío se descarta al guardar y no queda como cambio sin guardar
  it('después de guardar muestra lo guardado, sin el par vacío', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));
    await pulsar('Agregar dato');

    await pulsar('Guardar cambios');

    expect(pantalla().querySelector('#dato-nombre-1')).toBeNull();
    const pagina = harness.routeDebugElement!.componentInstance as ProductoPage;
    expect(pagina.tieneCambiosSinGuardar()).toBe(false);
  });

  // Al quitar la última fila, el foco no se pierde
  it('quitar el único dato lleva el foco a «Agregar dato»', async () => {
    await abrir('/productos/nuevo');
    await pulsar('Agregar dato');

    await pulsar('Quitar el dato 1');

    expect(document.activeElement?.id).toBe('agregar-dato');
  });

  // HU-07 RF-3, RF-6: lo que rechaza el backend se explica
  it('muestra el rechazo del backend', async () => {
    await abrir('/productos/7', (falso) => {
      falso.recurso.value.set(TALADRO);
      falso.respuesta = rechaza('FICHA_INVALIDA', 400);
    });

    await pulsar('Guardar cambios');

    expect(texto()).toContain('La ficha técnica tiene dos datos con el mismo nombre.');
  });

  it('marca el campo que rechazó el backend', async () => {
    await abrir('/productos/7', (falso) => {
      falso.recurso.value.set(TALADRO);
      falso.respuesta = rechaza('DATOS_INVALIDOS', 400, [{ campo: 'precio', mensaje: 'admite hasta 99 999,99' }]);
    });

    await pulsar('Guardar cambios');

    expect(texto()).toContain('El precio admite hasta 99 999,99.');
    expect(document.activeElement?.id).toBe('precio');
  });

  // HU-07 RF-7, RF-8: las fotos en orden, la primera es la principal
  it('muestra las fotos con la principal primero', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));

    const fotos = Array.from(pantalla().querySelectorAll<HTMLImageElement>('.foto img'));
    expect(fotos.map((foto) => foto.getAttribute('src'))).toEqual([
      '/api/productos/7/imagenes/a.png',
      '/api/productos/7/imagenes/b.png',
    ]);
    expect(texto()).toContain('Fotos 2 de 5');
    expect(texto()).toContain('Principal');
  });

  // HU-07 RF-8: la elegida pasa adelante y las demás conservan su orden
  it('hace principal otra foto', async () => {
    await abrir('/productos/7', (falso) => {
      falso.recurso.value.set({ ...TALADRO, imagenes: ['a.png', 'b.png', 'c.png'] });
      falso.respuesta = Promise.resolve({ ...TALADRO, imagenes: ['c.png', 'a.png', 'b.png'] });
    });

    await pulsar('Hacer principal la foto 3');

    expect(productos.ordenes).toEqual([{ id: 7, imagenes: ['c.png', 'a.png', 'b.png'] }]);
    expect(pantalla().querySelector('.foto img')?.getAttribute('src')).toBe('/api/productos/7/imagenes/c.png');
  });

  // HU-07 RF-8: quitar pregunta antes
  it('quita una foto después de confirmar', async () => {
    await abrir('/productos/7', (falso) => {
      falso.recurso.value.set(TALADRO);
      falso.respuesta = Promise.resolve({ ...TALADRO, imagenes: ['b.png'] });
    });
    vi.spyOn(dialogo(), 'abrir').mockResolvedValue(true);

    await pulsar('Quitar la foto 1');

    expect(productos.quitadas).toEqual(['a.png']);
    expect(texto()).toContain('Fotos 1 de 5');
    expect(document.activeElement?.id).toBe('titulo-fotos');
  });

  // HU-07 RF-7: sube una foto
  it('sube la foto elegida', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));
    const archivo = new File([new Uint8Array([0x89, 0x50])], 'foto.png', { type: 'image/png' });
    const entrada = campo('foto-nueva');
    Object.defineProperty(entrada, 'files', { value: [archivo] });

    entrada.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();

    expect(productos.subidas).toEqual([{ id: 7, archivo }]);
    expect(texto()).toContain('Foto agregada.');
  });

  // HU-07 RF-7: con 5 fotos no se ofrece subir otra; si llega a pasar, se explica
  it('con cinco fotos no ofrece subir otra', async () => {
    await abrir('/productos/7', (falso) =>
      falso.recurso.value.set({ ...TALADRO, imagenes: ['1.png', '2.png', '3.png', '4.png', '5.png'] }),
    );

    expect(pantalla().querySelector('#foto-nueva')).toBeNull();
    expect(texto()).toContain('Ya tiene 5 fotos: quite una para subir otra.');
  });

  it('explica una foto rechazada', async () => {
    await abrir('/productos/7', (falso) => {
      falso.recurso.value.set(TALADRO);
      falso.respuesta = rechaza('PRODUCTO_LLENO_DE_IMAGENES');
    });
    const entrada = campo('foto-nueva');
    Object.defineProperty(entrada, 'files', { value: [new File([], 'foto.png')] });

    entrada.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();

    expect(texto()).toContain('El producto ya tiene 5 fotos. Quite una antes de subir otra.');
  });

  it('avisa si la dirección no es la de un producto', async () => {
    await abrir('/productos/abc');

    expect(texto()).toContain('Esa dirección no es la de un producto.');
    expect(productos.id!()).toBeNull();
  });

  // Salir con cambios sin guardar pregunta (cambiosSinGuardarGuard)
  it('sabe si hay cambios sin guardar', async () => {
    await abrir('/productos/7', (falso) => falso.recurso.value.set(TALADRO));
    const pagina = harness.routeDebugElement!.componentInstance as ProductoPage;
    expect(pagina.tieneCambiosSinGuardar()).toBe(false);

    await escribir('marca', 'Makita');

    expect(pagina.tieneCambiosSinGuardar()).toBe(true);
  });

  // Auditoría (20/10/2026): sin las categorías el producto no se puede guardar; lo dice y deja reintentar
  it('si no cargan las categorías lo avisa y deja reintentar', async () => {
    await abrir('/productos/nuevo');
    categorias.recurso.value.set(undefined);
    categorias.recurso.error.set(new Error('sin conexión'));
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement?.textContent).toContain('No pudimos cargar las categorías.');
    const reintentar = Array.from(harness.routeNativeElement!.querySelectorAll('button')).find(
      (boton) => boton.textContent?.trim() === 'Reintentar',
    )!;
    reintentar.click();
    expect(categorias.recurso.reload).toHaveBeenCalled();
  });
});
