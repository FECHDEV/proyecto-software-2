import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CarritoService } from '../../core/api/carrito.service';
import { CuentaService } from '../../core/api/cuenta.service';
import { Carrito, LineaDeCarrito } from '../../core/modelos/carrito';
import { ErrorApi } from '../../core/modelos/error-api';
import { CarritoPage } from './carrito.page';

function linea(cambios: Partial<LineaDeCarrito>): LineaDeCarrito {
  return {
    idProducto: 7,
    nombre: 'Martillo',
    imagen: 'a.webp',
    precio: 85.5,
    unidad: 'UNIDAD',
    cantidad: 2,
    subtotal: 171,
    estado: 'DISPONIBLE',
    ...cambios,
  };
}

// Como responde GET /api/carrito (CarritoIntegracionTest)
const CON_DOS: Carrito = {
  lineas: [
    linea({}),
    linea({ idProducto: 9, nombre: 'Cable', imagen: null, precio: 12, unidad: 'METRO', cantidad: 2.5, subtotal: 30 }),
  ],
  total: 201,
  cantidadLineas: 2,
  idPedidoPendiente: null,
  correoVerificado: true,
};

const VACIO: Carrito = { lineas: [], total: 0, cantidadLineas: 0, idPedidoPendiente: null, correoVerificado: true };

class CarritoFalso {
  readonly carrito = {
    value: signal<Carrito | undefined>(undefined),
    error: signal<unknown>(undefined),
    isLoading: signal(false),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  // Como el servicio real: lo que devuelve la API queda en el recurso
  readonly cambiarCantidad = vi.fn((idProducto: number, cantidad: number) =>
    this.responder({
      ...CON_DOS,
      lineas: CON_DOS.lineas.map((cada) =>
        cada.idProducto === idProducto ? { ...cada, cantidad, subtotal: cada.precio * cantidad } : cada,
      ),
    }),
  );
  readonly quitar = vi.fn((idProducto: number) =>
    this.responder({ ...CON_DOS, lineas: CON_DOS.lineas.filter((cada) => cada.idProducto !== idProducto) }),
  );
  readonly vaciar = vi.fn(() => this.responder(VACIO));

  private responder(carrito: Carrito): Promise<Carrito> {
    this.carrito.value.set(carrito);
    return Promise.resolve(carrito);
  }
}

class CuentaFalsa {
  respuesta: Promise<string> = Promise.resolve('Le enviamos el correo a ana@mail.com.');
  readonly reenviarVerificacion = vi.fn(() => this.respuesta);
}

// HU-11: la página del carrito
describe('CarritoPage', () => {
  let harness: RouterTestingHarness;
  let carrito: CarritoFalso;
  let cuenta: CuentaFalsa;

  async function abrir(contenido: Carrito | undefined = CON_DOS): Promise<void> {
    TestBed.resetTestingModule();
    carrito = new CarritoFalso();
    cuenta = new CuentaFalsa();
    carrito.carrito.value.set(contenido);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'carrito', component: CarritoPage },
          { path: 'catalogo', children: [] },
          { path: 'producto/:id', children: [] },
        ]),
        { provide: CarritoService, useValue: carrito },
        { provide: CuentaService, useValue: cuenta },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/carrito');
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  }

  // El formato de precios separa «Bs» con un espacio duro
  function textoDe(elemento: Element | null | undefined): string {
    return (elemento?.textContent ?? '').replace(/\s+/g, ' ');
  }

  function fila(nombre: string): HTMLElement {
    const encontrada = Array.from(pantalla().querySelectorAll<HTMLElement>('.linea')).find((cada) =>
      cada.textContent?.includes(nombre),
    );
    if (encontrada === undefined) {
      throw new Error(`No hay línea de «${nombre}»`);
    }
    return encontrada;
  }

  function boton(textoDelBoton: string, dentro: HTMLElement = pantalla()): HTMLButtonElement {
    const encontrado = Array.from(dentro.querySelectorAll('button')).find((cada) =>
      (cada.getAttribute('aria-label') ?? cada.textContent?.trim())?.startsWith(textoDelBoton),
    );
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${textoDelBoton}»`);
    }
    return encontrado;
  }

  async function escribirCantidad(nombre: string, valor: string): Promise<void> {
    const campo = fila(nombre).querySelector('input')!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    campo.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  // HU-11 RF-8
  it('muestra las líneas y el total', async () => {
    await abrir();

    const martillo = fila('Martillo');
    expect(martillo.querySelector('a')?.getAttribute('href')).toBe('/producto/7');
    expect(martillo.querySelector('img')?.getAttribute('src')).toBe('/api/productos/7/imagenes/a.webp?ancho=400');
    expect(textoDe(martillo)).toContain('Bs 85,50');
    expect(textoDe(martillo)).toContain('Bs 171,00');
    expect(martillo.querySelector('input')?.value).toBe('2');
    const cable = fila('Cable');
    expect(cable.querySelector('img')).toBeNull();
    expect(textoDe(cable)).toContain('/ metro');
    expect(cable.querySelector('input')?.value).toBe('2,5');
    expect(textoDe(pantalla().querySelector('.total'))).toContain('Bs 201,00');
  });

  // HU-12 RF-1
  it('con todo disponible ofrece confirmar el pedido', async () => {
    await abrir();

    const confirmar = pantalla().querySelector('a[href="/confirmar-pedido"]');
    expect(textoDe(confirmar)).toContain('Confirmar pedido');
  });

  // HU-12 RF-3, RF-4: confirmar se rechazaría; el carrito dice qué corregir
  it('con alguna línea que no se puede comprar no ofrece confirmar', async () => {
    await abrir({ ...CON_DOS, lineas: [linea({ estado: 'AGOTADO', subtotal: null }), CON_DOS.lineas[1]], total: 30 });

    expect(pantalla().querySelector('a[href="/confirmar-pedido"]')).toBeNull();
    expect(texto()).toContain('Quite o corrija los productos marcados para confirmar el pedido.');
  });

  // HU-12 RF-5
  it('con un pedido pendiente lo avisa y enlaza a él', async () => {
    await abrir({ ...CON_DOS, idPedidoPendiente: 5 });

    expect(texto()).toContain('Tiene un pedido esperando el pago.');
    expect(pantalla().querySelector('a[href="/mis-pedidos/5"]')).not.toBeNull();
    expect(pantalla().querySelector('a[href="/confirmar-pedido"]')).toBeNull();
  });

  // HU-20 RF-7
  it('sin el correo verificado avisa en lugar de ofrecer confirmar', async () => {
    await abrir({ ...CON_DOS, correoVerificado: false });

    expect(texto()).toContain('Verifique su correo para confirmar');
    expect(pantalla().querySelector('a[href="/confirmar-pedido"]')).toBeNull();
    expect(boton('Reenviar el correo')).toBeTruthy();
  });

  // HU-20 RF-7: el resto del carrito funciona como siempre
  it('sin el correo verificado puede cambiar cantidades y quitar líneas', async () => {
    await abrir({ ...CON_DOS, correoVerificado: false });

    await escribirCantidad('Martillo', '3');
    boton('Quitar', fila('Cable')).click();
    await harness.fixture.whenStable();

    expect(carrito.cambiarCantidad).toHaveBeenCalledWith(7, 3);
    expect(carrito.quitar).toHaveBeenCalledWith(9);
  });

  // HU-20 RF-9
  it('Reenviar el correo avisa y no se manda dos veces mientras envía', async () => {
    let terminar!: (mensaje: string) => void;
    await abrir({ ...CON_DOS, correoVerificado: false });
    cuenta.respuesta = new Promise((resolver) => (terminar = resolver));

    boton('Reenviar el correo').click();
    await harness.fixture.whenStable();
    expect(boton('Enviando').getAttribute('aria-disabled')).toBe('true');
    boton('Enviando').click();
    terminar('Le enviamos el correo a ana@mail.com.');
    await harness.fixture.whenStable();

    expect(cuenta.reenviarVerificacion).toHaveBeenCalledTimes(1);
    expect(textoDe(pantalla().querySelector('.verificacion [aria-live]'))).toContain(
      'Le enviamos el correo a ana@mail.com.',
    );
  });

  // HU-20 RF-10
  it('el límite de reenvíos dice que espere', async () => {
    await abrir({ ...CON_DOS, correoVerificado: false });
    cuenta.respuesta = Promise.reject({ status: 429, detail: 'detalle', codigo: 'REENVIOS_EXCEDIDOS' });

    boton('Reenviar el correo').click();
    await harness.fixture.whenStable();

    expect(textoDe(pantalla().querySelector('.verificacion [aria-live]'))).toContain('Espere unos minutos');
  });

  // HU-20 RF-11: otra pestaña ya lo verificó; el carrito se pide de nuevo y ofrece confirmar
  it('si el correo ya estaba verificado recarga el carrito', async () => {
    await abrir({ ...CON_DOS, correoVerificado: false });
    carrito.carrito.reload.mockClear();
    cuenta.respuesta = Promise.reject({ status: 409, detail: 'detalle', codigo: 'CORREO_YA_VERIFICADO' });

    boton('Reenviar el correo').click();
    await harness.fixture.whenStable();

    expect(carrito.carrito.reload).toHaveBeenCalled();
  });

  // HU-20 RF-15
  it('con el correo verificado ofrece confirmar', async () => {
    await abrir({ ...CON_DOS, correoVerificado: true });

    expect(texto()).not.toContain('Verifique su correo');
    expect(pantalla().querySelector('a[href="/confirmar-pedido"]')).not.toBeNull();
  });

  // HU-11 RF-9
  it('las líneas agotadas o no disponibles van marcadas y fuera del total', async () => {
    await abrir({
      lineas: [
        linea({ estado: 'AGOTADO', subtotal: null }),
        linea({ idProducto: 8, nombre: 'Serrucho', estado: 'NO_DISPONIBLE', subtotal: null }),
        linea({ idProducto: 9, nombre: 'Cable', estado: 'CANTIDAD_INVALIDA', subtotal: null, cantidad: 2.5 }),
      ],
      total: 0,
      cantidadLineas: 3,
      idPedidoPendiente: null,
      correoVerificado: true,
    });

    expect(textoDe(fila('Martillo'))).toContain('Agotado');
    expect(textoDe(fila('Martillo'))).not.toContain('Bs 171,00');
    expect(fila('Martillo').querySelector('input')).toBeNull();
    expect(textoDe(fila('Serrucho'))).toContain('No disponible');
    expect(textoDe(fila('Cable'))).toContain('Corrija la cantidad');
    // La cantidad que la unidad ya no admite se puede corregir
    expect(fila('Cable').querySelector('input')).not.toBeNull();
    expect(boton('Quitar', fila('Serrucho'))).toBeTruthy();
  });

  // HU-11 RF-10
  it('cambiar la cantidad la guarda y recalcula', async () => {
    await abrir();

    await escribirCantidad('Martillo', '3');

    expect(carrito.cambiarCantidad).toHaveBeenCalledWith(7, 3);
    expect(textoDe(fila('Martillo'))).toContain('Bs 256,50');
  });

  // HU-11 RF-3, RF-10
  it('una cantidad inválida no se manda y dice por qué', async () => {
    await abrir();

    await escribirCantidad('Martillo', '1,5');

    expect(carrito.cambiarCantidad).not.toHaveBeenCalled();
    expect(textoDe(fila('Martillo'))).toContain('Este producto se cuenta en números enteros.');
  });

  // HU-11 RF-10
  it('una cantidad rechazada por la API vuelve a la anterior', async () => {
    await abrir();
    const rechazo: ErrorApi = {
      status: 400,
      detail: '',
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'cantidad', mensaje: 'admite hasta 999' }],
    };
    carrito.cambiarCantidad.mockReturnValueOnce(Promise.reject(rechazo));

    await escribirCantidad('Martillo', '999');

    expect(fila('Martillo').querySelector('input')?.value).toBe('2');
    expect(textoDe(fila('Martillo'))).toContain('La cantidad admite hasta 999.');
  });

  // HU-11 RF-11
  it('quitar una línea', async () => {
    await abrir();

    boton('Quitar', fila('Martillo')).click();
    await harness.fixture.whenStable();

    expect(carrito.quitar).toHaveBeenCalledWith(7);
    expect(textoDe(pantalla().querySelector('.lineas'))).not.toContain('Martillo');
  });

  // HU-11 RF-11
  it('vaciar pide confirmación', async () => {
    await abrir();
    const dialogo = () => pantalla().querySelector('dialog')!;

    boton('Vaciar carrito').click();
    await harness.fixture.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(true);
    (dialogo().querySelector('button.secundario') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    expect(carrito.vaciar).not.toHaveBeenCalled();

    boton('Vaciar carrito').click();
    await harness.fixture.whenStable();
    (dialogo().querySelector('button.principal') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    expect(carrito.vaciar).toHaveBeenCalled();
    // La respuesta del diálogo y la del servicio son dos promesas seguidas
    await harness.fixture.whenStable();
    expect(texto()).toContain('Su carrito está vacío');
  });

  // HU-11 RF-12
  it('el carrito vacío ofrece el catálogo', async () => {
    await abrir(VACIO);

    expect(texto()).toContain('Su carrito está vacío');
    const enlace = Array.from(pantalla().querySelectorAll('a')).find((cada) =>
      cada.textContent?.includes('Ver el catálogo'),
    );
    expect(enlace?.getAttribute('href')).toBe('/catalogo');
    expect(pantalla().querySelector('.total')).toBeNull();
  });

  // Caso límite: el carrito no se pudo cargar
  it('un error de red ofrece reintentar', async () => {
    await abrir(undefined);
    carrito.carrito.error.set({ status: 0 });
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor.');
    boton('Reintentar').click();
    expect(carrito.carrito.reload).toHaveBeenCalled();
  });

  // Revisión de HU-11 (interfaz): quitar no deja el foco perdido y lo anuncia
  it('quitar anuncia lo que quitó y lleva el foco al título', async () => {
    await abrir();
    const quitar = boton('Quitar', fila('Martillo'));
    quitar.focus();

    quitar.click();
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="status"]')?.textContent).toContain('Se quitó Martillo del carrito.');
    expect(document.activeElement).toBe(pantalla().querySelector('h1'));
  });

  // Revisión de HU-11 (interfaz): vaciar también lo anuncia y deja el foco en el título
  it('vaciar anuncia y lleva el foco al título', async () => {
    await abrir();

    boton('Vaciar carrito').click();
    await harness.fixture.whenStable();
    (pantalla().querySelector('dialog button.principal') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="status"]')?.textContent).toContain('Se vació el carrito.');
    expect(document.activeElement).toBe(pantalla().querySelector('h1'));
  });

  // Revisión de HU-11 (interfaz): el motivo de una línea se anuncia
  it('el error de una línea se anuncia', async () => {
    await abrir();

    await escribirCantidad('Martillo', '1,5');

    expect(fila('Martillo').querySelector('.error')?.getAttribute('role')).toBe('alert');
  });

  // Revisión de HU-11 (interfaz): la línea con cantidad inválida marca su campo y dice por qué
  it('la cantidad que la unidad ya no admite marca el campo', async () => {
    await abrir({
      lineas: [linea({ estado: 'CANTIDAD_INVALIDA', subtotal: null, cantidad: 1.5 })],
      total: 0,
      cantidadLineas: 1,
      idPedidoPendiente: null,
      correoVerificado: true,
    });

    const campo = fila('Martillo').querySelector('input')!;
    expect(campo.getAttribute('aria-invalid')).toBe('true');
    const descripcion = document.getElementById(campo.getAttribute('aria-describedby') ?? '');
    expect(textoDe(descripcion)).toContain('Corrija la cantidad');
  });

  // Revisión de HU-11 (/code-review): al abrir, el carrito se pide de nuevo con los precios de hoy
  it('al abrir vuelve a pedir el carrito', async () => {
    await abrir();

    expect(carrito.carrito.reload).toHaveBeenCalled();
  });

  // Revisión de HU-11 (/code-review): una línea que ya no existe (quitada en otro dispositivo) se va
  it('si la línea ya no existe, recarga el carrito', async () => {
    await abrir();
    carrito.carrito.reload.mockClear();
    carrito.cambiarCantidad.mockReturnValueOnce(
      Promise.reject({ status: 404, detail: '', codigo: 'PRODUCTO_NO_ENCONTRADO' }),
    );

    await escribirCantidad('Martillo', '3');

    expect(carrito.carrito.reload).toHaveBeenCalled();
  });

  // HU-11 RF-15
  it('− y + guardan la cantidad de a uno', async () => {
    await abrir();

    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();
    expect(carrito.cambiarCantidad).toHaveBeenLastCalledWith(7, 3);
    expect(textoDe(fila('Martillo'))).toContain('Bs 256,50');

    boton('Restar uno a Cable').click();
    await harness.fixture.whenStable();
    expect(carrito.cambiarCantidad).toHaveBeenLastCalledWith(9, 1.5);
  });

  // HU-11 RF-15
  it('− en 1 no hace nada', async () => {
    await abrir({ lineas: [linea({ cantidad: 1, subtotal: 85.5 })], total: 85.5, cantidadLineas: 1, idPedidoPendiente: null, correoVerificado: true });

    const restar = boton('Restar uno a Martillo');
    expect(restar.getAttribute('aria-disabled')).toBe('true');
    restar.click();
    await harness.fixture.whenStable();

    expect(carrito.cambiarCantidad).not.toHaveBeenCalled();
  });

  // HU-11 RF-15: un toque no espera la respuesta del anterior y ninguno se pierde
  it('dos toques seguidos suman dos', async () => {
    await abrir();
    carrito.cambiarCantidad.mockReturnValueOnce(new Promise(() => undefined));

    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();
    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();

    expect(carrito.cambiarCantidad).toHaveBeenNthCalledWith(1, 7, 3);
    expect(carrito.cambiarCantidad).toHaveBeenNthCalledWith(2, 7, 4);
    expect(fila('Martillo').querySelector('input')?.value).toBe('4');
  });

  // HU-11 RF-15: si la API rechaza, la cantidad vuelve a la guardada
  it('un + rechazado vuelve a la cantidad anterior', async () => {
    await abrir();
    carrito.cambiarCantidad.mockReturnValueOnce(
      Promise.reject({ status: 400, detail: '', codigo: 'DATOS_INVALIDOS', errores: [{ campo: 'cantidad', mensaje: 'admite hasta 999' }] }),
    );

    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();

    expect(fila('Martillo').querySelector('input')?.value).toBe('2');
    expect(textoDe(fila('Martillo'))).toContain('La cantidad admite hasta 999.');
  });

  // Revisión de HU-11 (/code-review): + parte de lo recién escrito, aunque todavía no se guardó
  it('escribir y tocar + enseguida suma a lo escrito', async () => {
    await abrir();
    carrito.cambiarCantidad.mockReturnValueOnce(new Promise(() => undefined));

    await escribirCantidad('Martillo', '5');
    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();

    expect(carrito.cambiarCantidad).toHaveBeenLastCalledWith(7, 6);
  });

  // Revisión de HU-11 (/code-review): si un toque falla y el siguiente se guarda, el error se va
  it('un toque rechazado y otro guardado después no dejan el error', async () => {
    await abrir();
    let rechazar!: (error: unknown) => void;
    let guardar!: () => void;
    const real = carrito.cambiarCantidad.getMockImplementation()!;
    carrito.cambiarCantidad
      .mockImplementationOnce(() => new Promise((_, falla) => (rechazar = falla)))
      .mockImplementationOnce((id: number, valor: number) => new Promise((listo) => (guardar = () => listo(real(id, valor)))));

    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();
    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();
    rechazar({ status: 400, detail: '', codigo: 'DATOS_INVALIDOS', errores: [{ campo: 'cantidad', mensaje: 'admite hasta 999' }] });
    await harness.fixture.whenStable();
    // Mientras la última está en camino, el rechazo de una anterior no se muestra
    expect(textoDe(fila('Martillo'))).not.toContain('La cantidad admite hasta 999.');
    guardar();
    await harness.fixture.whenStable();

    expect(fila('Martillo').querySelector('input')?.value).toBe('4');
    expect(textoDe(fila('Martillo'))).not.toContain('La cantidad admite hasta 999.');
  });

  // Revisión de HU-11 (/code-review): el lector de pantalla oye la cantidad guardada
  it('− y + anuncian la cantidad', async () => {
    await abrir();

    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();

    expect(textoDe(pantalla().querySelector('[role="status"]'))).toContain('Cantidad de Martillo: 3');
  });

  // Revisión de HU-11 (/code-review): un aviso viejo de quitar o vaciar se va al cambiar la cantidad
  it('cambiar con − o + borra el aviso anterior', async () => {
    await abrir();
    carrito.quitar.mockReturnValueOnce(Promise.reject({ status: 0, detail: '', codigo: 'SIN_CONEXION' }));
    boton('Quitar Cable').click();
    await harness.fixture.whenStable();
    expect(pantalla().querySelector('.aviso')).not.toBeNull();

    boton('Sumar uno a Martillo').click();
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('.aviso')).toBeNull();
  });
});
