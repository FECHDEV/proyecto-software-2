import { HttpErrorResponse } from '@angular/common/http';
import { effect, signal, Signal, untracked } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CarritoService } from '../../core/api/carrito.service';
import { CatalogoService } from '../../core/api/catalogo.service';
import { ProductoEnDetalle, ProductoEnTienda } from '../../core/modelos/catalogo';
import { Rol } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { CARGAR_VISOR_3D } from '../../shared/visor-3d/visor-3d';
import { DetallePage } from './detalle.page';

function tarjeta(idProducto: number, nombre: string): ProductoEnTienda {
  return { idProducto, nombre, marca: null, precio: 85, unidad: 'UNIDAD', imagenPrincipal: null, disponible: true, conModelo: false };
}

// Como responde GET /api/productos/7 (CatalogoIntegracionTest)
const TALADRO: ProductoEnDetalle = {
  idProducto: 7,
  codigo: 'HER-0007',
  nombre: 'Taladro percutor 750 W',
  marca: 'Bosch',
  idCategoria: 3,
  categoria: 'Herramientas',
  precio: 420,
  unidad: 'UNIDAD',
  descripcion: 'Para concreto y madera.\nCon maletín.',
  ficha: [
    { nombre: 'Potencia', valor: '750 W' },
    { nombre: 'Mandril', valor: '13 mm' },
  ],
  imagenes: ['a.webp', 'b.webp'],
  disponible: true,
  modelo: null,
  relacionados: [tarjeta(8, 'Martillo')],
};

const MARTILLO: ProductoEnDetalle = {
  ...TALADRO,
  idProducto: 8,
  codigo: 'HER-0008',
  nombre: 'Martillo',
  imagenes: [],
  relacionados: [tarjeta(7, 'Taladro percutor 750 W')],
};

class RecursoFalso<T> {
  readonly value = signal<T | undefined>(undefined);
  readonly isLoading = signal(false);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class CatalogoFalso {
  readonly recurso = new RecursoFalso<ProductoEnDetalle>();
  id: Signal<number | null> | null = null;
  pedidos = 0;
  // Contesta según el id, como la API
  respuestas = new Map<number, ProductoEnDetalle>([
    [7, TALADRO],
    [8, MARTILLO],
  ]);
  // Como httpResource: con otro id, el valor y el error anteriores se van
  detalle(id: Signal<number | null>) {
    this.id = id;
    effect(() => {
      id();
      untracked(() => {
        this.recurso.value.set(undefined);
        this.recurso.error.set(undefined);
      });
    });
    return this.recurso;
  }
  responder(): void {
    const id = this.id?.() ?? null;
    if (id !== null) {
      this.pedidos++;
      this.recurso.value.set(this.respuestas.get(id));
    }
  }
}

// HU-10: el detalle del producto
describe('DetallePage', () => {
  let harness: RouterTestingHarness;
  let catalogo: CatalogoFalso;
  let carrito: { agregar: ReturnType<typeof vi.fn> };

  async function abrir(url = '/producto/7', rol: Rol | null = null, responder = true) {
    TestBed.resetTestingModule();
    catalogo = new CatalogoFalso();
    carrito = { agregar: vi.fn(() => Promise.resolve({ lineas: [], total: 0, cantidadLineas: 1, idPedidoPendiente: null, correoVerificado: true })) };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'producto/:id', component: DetallePage },
          { path: 'catalogo', children: [] },
          { path: 'productos/:id', children: [] },
          { path: 'inicio-sesion', children: [] },
          { path: 'carrito', children: [] },
        ]),
        { provide: CatalogoService, useValue: catalogo },
        { provide: CarritoService, useValue: carrito },
        { provide: SesionService, useValue: { rol: signal(rol) } },
        { provide: CARGAR_VISOR_3D, useValue: () => Promise.resolve(true) },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    if (responder) {
      catalogo.responder();
    }
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  }

  function enlace(textoDelEnlace: string): HTMLAnchorElement | undefined {
    return Array.from(pantalla().querySelectorAll('a')).find((cada) => cada.textContent?.trim() === textoDelEnlace);
  }

  // HU-16 RF-1, RF-3: la galería recibe la dirección del modelo
  it('pasa la dirección del modelo a la galería', async () => {
    await abrir('/producto/7', null, false);
    catalogo.recurso.value.set({ ...TALADRO, modelo: 'm b.glb' });
    await harness.fixture.whenStable();

    const boton = pantalla().querySelector<HTMLButtonElement>('app-galeria .vista')!;
    expect(boton.textContent).toContain('Ver en 3D');
    boton.click();
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('model-viewer')?.getAttribute('src')).toBe('/api/productos/7/modelo/m%20b.glb');
  });

  // HU-16 RF-3: sin modelo, la galería de HU-10
  it('sin modelo la galería es la de HU-10', async () => {
    await abrir();

    expect(pantalla().querySelector('app-galeria .vista')).toBeNull();
    expect(texto()).not.toContain('Ver en 3D');
  });

  // HU-10 RF-1: sin guard, para cualquiera
  it('abre sin sesión', async () => {
    await abrir();

    expect(pantalla().querySelector('h1')?.textContent?.trim()).toBe('Taladro percutor 750 W');
  });

  // Revisión de HU-10: al entrar, el foco no se mueve (solo al pasar de un producto a otro)
  it('al entrar no mueve el foco', async () => {
    await abrir();

    expect(document.activeElement).not.toBe(pantalla().querySelector('h1'));
  });

  // HU-10 RF-2
  it('muestra los datos del producto', async () => {
    await abrir();

    expect(texto()).toContain('Bosch');
    expect(texto()).toContain('HER-0007');
    expect(texto()).toContain('Herramientas');
    expect(texto()).toContain('Bs 420,00');
    expect(pantalla().querySelector('.descripcion')?.textContent).toContain('Para concreto y madera.\nCon maletín.');
    const ficha = Array.from(pantalla().querySelectorAll('.ficha dt, .ficha dd')).map((cada) => cada.textContent?.trim());
    expect(ficha).toEqual(['Potencia', '750 W', 'Mandril', '13 mm']);
    const fotos = Array.from(pantalla().querySelectorAll('app-galeria .miniaturas img')).map((cada) =>
      cada.getAttribute('src'),
    );
    expect(fotos).toEqual(['/api/productos/7/imagenes/a.webp', '/api/productos/7/imagenes/b.webp']);
  });

  // HU-10 RF-2: el precio va con su unidad cuando no es «unidad»
  it('el precio lleva la unidad', async () => {
    await abrir('/producto/7', null, false);
    catalogo.recurso.value.set({ ...TALADRO, precio: 12.5, unidad: 'METRO' });
    await harness.fixture.whenStable();

    expect(texto()).toContain('Bs 12,50 / metro');
  });

  // HU-10 RF-2
  it('sin marca, descripción ni ficha esas partes no salen', async () => {
    await abrir('/producto/7', null, false);
    catalogo.recurso.value.set({ ...TALADRO, marca: null, descripcion: null, ficha: [] });
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('.datos .marca')).toBeNull();
    expect(pantalla().querySelector('.descripcion')).toBeNull();
    expect(pantalla().querySelector('.ficha')).toBeNull();
    expect(texto()).toContain('HER-0007');
  });

  // HU-10 RF-3
  it('la categoría lleva al catálogo filtrado', async () => {
    await abrir();

    enlace('Herramientas')?.click();
    await harness.fixture.whenStable();

    expect(TestBed.inject(Router).url).toBe('/catalogo?categoria=3');
  });

  // HU-10 RF-4: nunca la cantidad
  it('dice si está disponible o agotado, sin la cantidad', async () => {
    await abrir();
    expect(pantalla().querySelector('.disponibilidad')?.textContent?.trim()).toBe('Disponible');

    catalogo.recurso.value.set({ ...TALADRO, disponible: false });
    await harness.fixture.whenStable();
    const marca = pantalla().querySelector('.disponibilidad');
    expect(marca?.textContent?.trim()).toBe('Agotado');
    expect(marca?.querySelector('svg')).not.toBeNull();
    expect(texto()).not.toMatch(/stock/i);
  });

  // HU-10 RF-7
  it('muestra «Más de» la categoría', async () => {
    await abrir();

    const carrusel = pantalla().querySelector('app-carrusel');
    expect(carrusel?.querySelector('h2')?.textContent?.trim()).toBe('Más de Herramientas');
    expect(carrusel?.textContent).toContain('Martillo');
  });

  // HU-10 RF-7
  it('sin relacionados no sale el carrusel', async () => {
    await abrir('/producto/7', null, false);
    catalogo.recurso.value.set({ ...TALADRO, relacionados: [] });
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('app-carrusel')).toBeNull();
  });

  // HU-10 RF-8: inactivo, oculto o inexistente responden igual
  it('un producto que no está disponible lo dice', async () => {
    await abrir('/producto/7', null, false);
    catalogo.recurso.error.set(new HttpErrorResponse({ status: 404 }));
    await harness.fixture.whenStable();

    expect(texto()).toContain('Este producto no está disponible');
    expect(enlace('Ver el catálogo')?.getAttribute('href')).toBe('/catalogo');
    expect(pantalla().querySelector('button')).toBeNull();
  });

  // HU-10 RF-9: el aviso de RF-8, sin consultar la API
  it('una dirección basura no consulta la API', async () => {
    for (const basura of ['abc', '-1', '0', '1.5', '99999999999999999999']) {
      await abrir(`/producto/${basura}`);

      expect(catalogo.id?.()).toBeNull();
      expect(catalogo.pedidos).toBe(0);
      expect(texto()).toContain('Este producto no está disponible');
    }
  });

  // HU-10 RF-10: usabilidad; el permiso lo valida el backend
  it('«Editar producto» solo para el Administrador', async () => {
    await abrir('/producto/7', 'ADMINISTRADOR');
    expect(enlace('Editar producto')?.getAttribute('href')).toBe('/productos/7');

    for (const rol of [null, 'CLIENTE', 'EMPLEADO', 'CONTADOR'] as const) {
      await abrir('/producto/7', rol);
      expect(enlace('Editar producto')).toBeUndefined();
    }
  });

  // HU-10 RF-11
  it('pone el nombre en la pestaña', async () => {
    await abrir();

    expect(TestBed.inject(Title).getTitle()).toBe('Taladro percutor 750 W · Tienda Montero');
  });

  // Caso límite: un error que no es 404 se puede reintentar
  it('un error de red ofrece reintentar', async () => {
    await abrir('/producto/7', null, false);
    catalogo.recurso.error.set(new HttpErrorResponse({ status: 0 }));
    await harness.fixture.whenStable();

    expect(texto()).not.toContain('Este producto no está disponible');
    const reintentar = Array.from(pantalla().querySelectorAll('button')).find(
      (cada) => cada.textContent?.trim() === 'Reintentar',
    );
    reintentar?.click();
    expect(catalogo.recurso.reload).toHaveBeenCalled();
  });

  // Caso límite: de un producto a otro, desde «Más de…»
  it('pasar a otro producto carga el nuevo y lleva el foco al título', async () => {
    await abrir();

    pantalla().querySelector<HTMLAnchorElement>('app-tarjeta-producto a')?.click();
    await harness.fixture.whenStable();
    // Mientras llega el nuevo, no queda el anterior
    expect(texto()).toContain('Cargando el producto');
    expect(pantalla().querySelector('h1')).toBeNull();
    catalogo.responder();
    await harness.fixture.whenStable();

    expect(catalogo.id?.()).toBe(8);
    const titulo = pantalla().querySelector('h1');
    expect(titulo?.textContent?.trim()).toBe('Martillo');
    expect(document.activeElement).toBe(titulo);
    expect(pantalla().querySelector('app-galeria .miniaturas')).toBeNull();
  });

  // --- HU-11: agregar al carrito ---

  function campoCantidad(): HTMLInputElement | null {
    return pantalla().querySelector<HTMLInputElement>('#cantidad');
  }

  function botonAgregar(): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll('button')).find((cada) =>
      cada.textContent?.includes('Agregar al carrito'),
    );
  }

  async function agregar(cantidad?: string): Promise<void> {
    const campo = campoCantidad();
    if (campo !== null && cantidad !== undefined) {
      campo.value = cantidad;
      campo.dispatchEvent(new Event('input'));
    }
    botonAgregar()?.click();
    await harness.fixture.whenStable();
  }

  // HU-11 RF-1
  it('el cliente agrega con la cantidad elegida', async () => {
    await abrir('/producto/7', 'CLIENTE');
    expect(campoCantidad()?.value).toBe('1');

    await agregar('2');

    expect(carrito.agregar).toHaveBeenCalledWith(7, 2);
    expect(texto()).toContain('Se agregó al carrito');
    expect(enlace('Ver carrito')?.getAttribute('href')).toBe('/carrito');
  });

  // HU-11 RF-3
  it('valida la cantidad según la unidad', async () => {
    await abrir('/producto/7', 'CLIENTE');
    await agregar('1,5');
    expect(texto()).toContain('Este producto se cuenta en números enteros.');
    await agregar('1000');
    expect(texto()).toContain('La cantidad admite hasta 999.');
    await agregar('0');
    expect(texto()).toContain('La cantidad tiene que ser mayor que 0.');
    expect(carrito.agregar).not.toHaveBeenCalled();

    catalogo.recurso.value.set({ ...TALADRO, unidad: 'METRO' });
    await harness.fixture.whenStable();
    await agregar('2,5');
    expect(carrito.agregar).toHaveBeenCalledWith(7, 2.5);
  });

  // HU-11 RF-4
  it('un producto agotado no se puede agregar', async () => {
    await abrir('/producto/7', 'CLIENTE', false);
    catalogo.recurso.value.set({ ...TALADRO, disponible: false });
    await harness.fixture.whenStable();

    expect(botonAgregar()?.disabled).toBe(true);
    expect(campoCantidad()).toBeNull();
  });

  // HU-11 RF-6
  it('sin sesión lleva a iniciar sesión y vuelve al producto', async () => {
    await abrir('/producto/7', null);
    expect(campoCantidad()).toBeNull();

    await agregar();

    expect(carrito.agregar).not.toHaveBeenCalled();
    expect(TestBed.inject(Router).url).toBe('/inicio-sesion?destino=%2Fproducto%2F7');
  });

  // HU-11 RF-7
  it('el personal no ve el botón', async () => {
    for (const rol of ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] as const) {
      await abrir('/producto/7', rol);
      expect(botonAgregar()).toBeUndefined();
      expect(campoCantidad()).toBeNull();
    }
  });

  // HU-11 RF-3, RF-4, RF-5
  it.each([
    [{ status: 409, detail: '', codigo: 'PRODUCTO_AGOTADO' }, 'Este producto está agotado.'],
    [{ status: 409, detail: '', codigo: 'CARRITO_LLENO' }, 'Su carrito ya tiene 50 productos distintos.'],
    [{ status: 404, detail: '', codigo: 'PRODUCTO_NO_ENCONTRADO' }, 'Este producto ya no está disponible.'],
  ])('muestra el error de la API (%o)', async (rechazo, mensaje) => {
    await abrir('/producto/7', 'CLIENTE');
    carrito.agregar.mockReturnValueOnce(Promise.reject(rechazo));

    await agregar('1');

    expect(texto()).toContain(mensaje);
    expect(texto()).not.toContain('Se agregó al carrito');
  });

  // HU-11, caso límite: otro producto desde «Más de…» empieza de cero
  it('al pasar a otro producto la cantidad vuelve a 1 y se va el aviso', async () => {
    await abrir('/producto/7', 'CLIENTE');
    await agregar('3');
    expect(texto()).toContain('Se agregó al carrito');

    pantalla().querySelector<HTMLAnchorElement>('app-tarjeta-producto a')?.click();
    await harness.fixture.whenStable();
    catalogo.responder();
    await harness.fixture.whenStable();

    expect(campoCantidad()?.value).toBe('1');
    expect(texto()).not.toContain('Se agregó al carrito');
  });

  // Revisión de HU-11 (interfaz): solo espacios no es una cantidad
  it('una cantidad en blanco no se manda', async () => {
    await abrir('/producto/7', 'CLIENTE');

    await agregar('   ');

    expect(carrito.agregar).not.toHaveBeenCalled();
    expect(campoCantidad()?.getAttribute('aria-invalid')).toBe('true');
    expect(texto()).toContain('Escriba la cantidad.');
  });

  // Revisión de HU-11 (interfaz): el rechazo de la suma va junto al campo y dice por qué
  it('si la suma pasa de 999, lo dice junto al campo', async () => {
    await abrir('/producto/7', 'CLIENTE');
    carrito.agregar.mockReturnValueOnce(
      Promise.reject({
        status: 400,
        detail: '',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'cantidad', mensaje: 'sumada a la del carrito pasaría de 999' }],
      }),
    );

    await agregar('998');

    expect(campoCantidad()?.getAttribute('aria-invalid')).toBe('true');
    expect(pantalla().querySelector('.campo')?.textContent).toContain('La cantidad sumada a la del carrito pasaría de 999.');
    expect(pantalla().querySelector('.resultado .aviso')).toBeNull();
    // Al cambiar la cantidad, el motivo se va
    await agregar('1');
    expect(campoCantidad()?.getAttribute('aria-invalid')).toBeNull();
  });

  // Revisión de HU-11 (interfaz): mientras agrega, el botón no se deshabilita y no pierde el foco
  it('mientras agrega, el botón sigue con el foco', async () => {
    await abrir('/producto/7', 'CLIENTE');
    carrito.agregar.mockReturnValueOnce(new Promise(() => undefined));
    botonAgregar()?.focus();

    botonAgregar()?.click();
    await harness.fixture.whenStable();

    expect(botonAgregar()?.disabled).toBe(false);
    expect(botonAgregar()?.getAttribute('aria-disabled')).toBe('true');
    expect(document.activeElement).toBe(botonAgregar());
  });

  function botonPaso(etiqueta: 'Restar uno' | 'Sumar uno'): HTMLButtonElement {
    return pantalla().querySelector<HTMLButtonElement>(`button[aria-label="${etiqueta}"]`)!;
  }

  async function tocar(etiqueta: 'Restar uno' | 'Sumar uno'): Promise<void> {
    botonPaso(etiqueta).click();
    await harness.fixture.whenStable();
  }

  // HU-11 RF-15
  it('− y + cambian la cantidad de a uno', async () => {
    await abrir('/producto/7', 'CLIENTE');
    expect(botonPaso('Restar uno').getAttribute('aria-disabled')).toBe('true');

    await tocar('Sumar uno');
    await tocar('Sumar uno');
    expect(campoCantidad()?.value).toBe('3');
    await tocar('Restar uno');
    expect(campoCantidad()?.value).toBe('2');

    await agregar();
    expect(carrito.agregar).toHaveBeenCalledWith(7, 2);
  });

  // HU-11 RF-15: sin bajar de 1 ni pasar de 999; con decimales, los conserva
  it('− no baja de 1 y + no pasa de 999', async () => {
    await abrir('/producto/7', 'CLIENTE');

    await tocar('Restar uno');
    expect(campoCantidad()?.value).toBe('1');

    await agregar('999');
    expect(botonPaso('Sumar uno').getAttribute('aria-disabled')).toBe('true');
    await tocar('Sumar uno');
    expect(campoCantidad()?.value).toBe('999');

    catalogo.recurso.value.set({ ...TALADRO, unidad: 'METRO' });
    await harness.fixture.whenStable();
    await agregar('2,5');
    await tocar('Sumar uno');
    expect(campoCantidad()?.value).toBe('3,5');
  });

  // Revisión de HU-11 (/code-review): lo que no es un número no tiene desde dónde sumar
  it('con algo que no es un número, + no hace nada', async () => {
    await abrir('/producto/7', 'CLIENTE');
    const campo = campoCantidad()!;
    campo.value = 'abc';
    campo.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();

    expect(botonPaso('Sumar uno').getAttribute('aria-disabled')).toBe('true');
    await tocar('Sumar uno');

    expect(campoCantidad()?.value).toBe('abc');
  });

  // Revisión de HU-11 (/code-review): el aviso de lo agregado no queda junto a otra cantidad
  it('tocar − o + después de agregar quita el aviso', async () => {
    await abrir('/producto/7', 'CLIENTE');
    await agregar('2');
    expect(texto()).toContain('Se agregó al carrito');

    await tocar('Sumar uno');

    expect(texto()).not.toContain('Se agregó al carrito');
  });

  // Revisión de HU-11 (/code-review): por encima de 999, − la vuelve a 999
  it('con más de 999, − la deja en 999', async () => {
    await abrir('/producto/7', 'CLIENTE');
    await agregar('5000');

    await tocar('Restar uno');

    expect(campoCantidad()?.value).toBe('999');
  });

  // Revisión de HU-11 (/code-review): el lector de pantalla oye la cantidad nueva
  it('− y + anuncian la cantidad', async () => {
    await abrir('/producto/7', 'CLIENTE');

    await tocar('Sumar uno');

    expect(pantalla().querySelector('app-agregar-al-carrito [role="status"]')?.textContent).toContain('Cantidad: 2');
  });
});
