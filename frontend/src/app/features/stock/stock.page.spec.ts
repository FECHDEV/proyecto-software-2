import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { InventarioService } from '../../core/api/inventario.service';
import { ErrorApi } from '../../core/modelos/error-api';
import { MotivoAjuste, Movimiento, ProductoEnInventario } from '../../core/modelos/inventario';
import { Pagina } from '../../core/modelos/pagina';
import { Sesion } from '../../core/modelos/sesion';
import { Rol } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { StockPage } from './stock.page';

// Como responde GET /api/inventario/7 y /movimientos (InventarioIntegracionTest)
const TALADRO: ProductoEnInventario = {
  idProducto: 7,
  codigo: 'HER-0007',
  nombre: 'Taladro percutor 750 W',
  categoria: 'Herramientas',
  unidad: 'UNIDAD',
  activo: true,
  stock: 3,
  stockMinimo: 5,
  stockBajo: true,
  comprometido: 0,
};

const MANGUERA: ProductoEnInventario = { ...TALADRO, idProducto: 8, nombre: 'Manguera', unidad: 'METRO', stockMinimo: null, stockBajo: false };

const MOVIMIENTOS: Movimiento[] = [
  {
    idMovimiento: 2,
    fecha: '2026-10-12T15:30:00',
    tipo: 'AJUSTE',
    motivo: 'MERMA',
    cantidad: -2,
    stockResultante: 3,
    usuario: 'Eva Paz',
    nota: null,
    idPedido: null,
    contado: null,
    comprometido: null,
  },
  {
    idMovimiento: 1,
    fecha: '2026-10-12T09:00:00',
    tipo: 'ENTRADA',
    motivo: null,
    cantidad: 5,
    stockResultante: 5,
    usuario: 'Eva Paz',
    nota: 'Factura 123',
    idPedido: null,
    contado: null,
    comprometido: null,
  },
];

function sesionCon(rol: Rol): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: {
      idUsuario: 4,
      nombre: 'Eva',
      apellido: 'Paz',
      correo: 'eva@mail.com',
      telefono: null,
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
      correoVerificado: true,
    },
  };
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
  producto = new RecursoFalso<ProductoEnInventario>(TALADRO);
  movimientos = new RecursoFalso<Pagina<Movimiento>>({
    contenido: MOVIMIENTOS,
    pagina: 0,
    tamano: 20,
    totalElementos: 2,
    totalPaginas: 1,
  });
  id: Signal<number | null> | null = null;
  respuesta: Promise<ProductoEnInventario> = Promise.resolve({ ...TALADRO, stock: 13, stockBajo: false });
  entradas: Array<{ id: number; cantidad: number; nota: string }> = [];
  ajustes: Array<{ id: number; contada: number; motivo: MotivoAjuste; nota: string }> = [];
  minimos: Array<{ id: number; minimo: number | null }> = [];

  // La pantalla pide el producto primero y el historial después
  productoPedido(id: Signal<number | null>) {
    this.id = id;
    return this.producto;
  }
  historial() {
    return this.movimientos;
  }
  registrarEntrada(id: number, cantidad: number, nota: string) {
    this.entradas.push({ id, cantidad, nota });
    return this.respuesta;
  }
  registrarAjuste(id: number, contada: number, motivo: MotivoAjuste, nota: string) {
    this.ajustes.push({ id, contada, motivo, nota });
    return this.respuesta;
  }
  cambiarMinimo(id: number, minimo: number | null) {
    this.minimos.push({ id, minimo });
    return this.respuesta;
  }
}

// HU-08: el stock de un producto y su historial
describe('StockPage', () => {
  let harness: RouterTestingHarness;
  let inventario: InventarioFalso;

  async function abrir(url = '/inventario/7', rol: Rol = 'EMPLEADO', configurar: (falso: InventarioFalso) => void = () => {}) {
    TestBed.resetTestingModule();
    localStorage.clear();
    inventario = new InventarioFalso();
    configurar(inventario);
    const servicio = {
      producto: (id: Signal<number | null>) => inventario.productoPedido(id),
      historial: () => inventario.historial(),
      registrarEntrada: (id: number, cantidad: number, nota: string) => inventario.registrarEntrada(id, cantidad, nota),
      registrarAjuste: (id: number, contada: number, motivo: MotivoAjuste, nota: string) =>
        inventario.registrarAjuste(id, contada, motivo, nota),
      cambiarMinimo: (id: number, minimo: number | null) => inventario.cambiarMinimo(id, minimo),
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'inventario/:id', component: StockPage }]),
        { provide: InventarioService, useValue: servicio },
      ],
    });
    TestBed.inject(SesionService).iniciar(sesionCon(rol));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, StockPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  afterEach(() => localStorage.clear());

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  }

  async function escribir(id: string, valor: string) {
    const campo = pantalla().querySelector<HTMLInputElement | HTMLSelectElement>('#' + id)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    campo.dispatchEvent(new Event('change'));
    campo.dispatchEvent(new Event('blur'));
    await harness.fixture.whenStable();
  }

  // El envío termina después de la validación de Signal Forms: se espera una vuelta más
  async function pulsar(nombre: string) {
    const boton = Array.from(pantalla().querySelectorAll('button')).find((cada) => cada.textContent?.trim() === nombre);
    if (boton === undefined) {
      throw new Error(`No hay botón «${nombre}»`);
    }
    boton.click();
    await harness.fixture.whenStable();
    await new Promise((listo) => setTimeout(listo));
    await harness.fixture.whenStable();
  }

  // 16/10/2026: con stock 0 la marca es «Agotado», no «Stock bajo»
  it('marca como agotado un producto sin stock', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) => falso.producto.value.set({ ...TALADRO, stock: 0 }));

    const marca = (pantalla().querySelector('.bajo')?.textContent ?? '').trim();
    expect(marca).toBe('Agotado');
  });

  // HU-08 RF-9, RF-11: cabecera con stock, mínimo y la marca; historial con signo, motivo, quién y la nota
  it('muestra el stock, el mínimo y el historial', async () => {
    await abrir();

    expect(inventario.id!()).toBe(7);
    expect(texto()).toContain('Taladro percutor 750 W');
    expect(texto()).toContain('Disponible3 unidad');
    expect(texto()).toContain('Mínimo5');
    expect(texto()).toContain('Stock bajo');
    expect(texto()).toContain('Ajuste · Daño o merma');
    expect(texto()).toContain('−2');
    expect(texto()).toContain('+5');
    expect(texto()).toContain('Eva Paz');
    expect(texto()).toContain('Factura 123');
    expect(texto()).toContain('15:30');
  });

  // HU-12 RF-14
  it('el historial muestra los movimientos de pedido con su número', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) =>
      falso.movimientos.value.set({
        contenido: [
          { ...MOVIMIENTOS[1], idMovimiento: 4, tipo: 'LIBERACION', cantidad: 2, nota: null, idPedido: 12 },
          { ...MOVIMIENTOS[1], idMovimiento: 3, tipo: 'RESERVA', cantidad: -2, nota: null, idPedido: 12 },
        ],
        pagina: 0,
        tamano: 20,
        totalElementos: 2,
        totalPaginas: 1,
      }),
    );

    expect(texto()).toContain('Liberación por pedido N.º 12');
    expect(texto()).toContain('Reserva por pedido N.º 12');
    expect(texto()).toContain('−2');
  });

  // HU-08 RF-3
  it('registra una entrada y actualiza el stock', async () => {
    await abrir();
    await escribir('entrada-cantidad', '10');
    await escribir('entrada-nota', 'Factura 124');

    await pulsar('Registrar entrada');

    expect(inventario.entradas).toEqual([{ id: 7, cantidad: 10, nota: 'Factura 124' }]);
    expect(texto()).toContain('Entrada registrada. Disponible: 13.');
    expect(texto()).toContain('Disponible13 unidad');
    expect(inventario.movimientos.reload).toHaveBeenCalled();
    expect((pantalla().querySelector('#entrada-cantidad') as HTMLInputElement).value).toBe('');
  });

  // HU-08 RF-5: los decimales según la unidad
  it('no envía decimales en un producto que se cuenta por unidad', async () => {
    await abrir();
    await escribir('entrada-cantidad', '2,5');

    await pulsar('Registrar entrada');

    expect(texto()).toContain('Este producto se cuenta en números enteros.');
    expect(inventario.entradas).toEqual([]);
    expect(document.activeElement?.id).toBe('entrada-cantidad');
  });

  // HU-08 RF-5: por metro se aceptan con coma
  it('acepta decimales con coma en un producto por metro', async () => {
    await abrir('/inventario/8', 'EMPLEADO', (falso) => falso.producto.value.set(MANGUERA));
    await escribir('entrada-cantidad', '25,5');

    await pulsar('Registrar entrada');

    expect(inventario.entradas[0].cantidad).toBe(25.5);
  });

  // HU-08 RF-4, RF-7: con «Otro» la nota es obligatoria
  it('registra un ajuste y exige la nota con el motivo «Otro»', async () => {
    await abrir();
    await escribir('ajuste-contada', '8');
    await escribir('ajuste-motivo', 'OTRO');

    await pulsar('Registrar ajuste');
    expect(texto()).toContain('Con el motivo «Otro», explica qué pasó.');
    expect(texto()).toContain('(obligatoria)');
    expect(pantalla().querySelector('#ajuste-nota')?.getAttribute('aria-required')).toBe('true');
    expect(inventario.ajustes).toEqual([]);

    await escribir('ajuste-nota', 'Se usó en el taller');
    await pulsar('Registrar ajuste');
    expect(inventario.ajustes).toEqual([{ id: 7, contada: 8, motivo: 'OTRO', nota: 'Se usó en el taller' }]);
    expect(texto()).toContain('Ajuste registrado.');
  });

  // HU-08 RF-6: el rechazo del backend se explica junto al formulario
  it('explica un ajuste sin cambio', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) => {
      falso.respuesta = Promise.reject({ status: 409, detail: 'x', codigo: 'AJUSTE_SIN_CAMBIO' } as ErrorApi);
    });
    await escribir('ajuste-contada', '3');

    await pulsar('Registrar ajuste');

    expect(texto()).toContain('La cantidad contada es igual a lo que hay en el estante: no hay nada que ajustar.');
  });

  // HU-08 RF-18: el rechazo dice cuánto está comprometido
  it('explica un conteo menor que lo comprometido', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) => {
      falso.respuesta = Promise.reject({
        status: 409,
        detail: 'x',
        codigo: 'CONTEO_MENOR_QUE_COMPROMETIDO',
        comprometido: 5,
      } as ErrorApi);
    });
    await escribir('ajuste-contada', '4');

    await pulsar('Registrar ajuste');

    expect(texto()).toContain('Contaron menos de lo que ya está vendido o reservado (5). Revise el conteo.');
  });

  // HU-08 RF-16, RF-17: disponible, comprometido y lo que hay en el estante; el ajuste lo explica
  it('muestra lo comprometido y lo que hay en el estante', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) => falso.producto.value.set({ ...TALADRO, stock: 10, comprometido: 5 }));

    expect(texto()).toContain('Disponible10 unidad');
    expect(texto()).toContain('Comprometido5 unidad');
    expect(texto()).toContain('En el estante 15 unidad');
    expect(texto()).toContain('incluido lo apartado para pedidos (5 unidad)');
  });

  // HU-08 RF-17: el historial de un ajuste dice lo contado y lo comprometido
  it('el historial muestra lo contado y lo comprometido de un ajuste', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) =>
      falso.movimientos.value.set({
        contenido: [{ ...MOVIMIENTOS[0], contado: 14, comprometido: 5 }],
        pagina: 0,
        tamano: 20,
        totalElementos: 1,
        totalPaginas: 1,
      }),
    );

    expect(texto()).toContain('Ajuste · Daño o merma · en el estante 14, comprometido 5');
  });

  it('explica un dato que rechazó el backend con su campo', async () => {
    await abrir('/inventario/7', 'EMPLEADO', (falso) => {
      falso.respuesta = Promise.reject({
        status: 400,
        detail: 'x',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'cantidad', mensaje: 'tiene que ser un número entero' }],
      } as ErrorApi);
    });
    await escribir('entrada-cantidad', '2');

    await pulsar('Registrar entrada');

    expect(texto()).toContain('La cantidad tiene que ser un número entero.');
  });

  // HU-08 RF-9
  it('guarda y quita el mínimo', async () => {
    await abrir();
    expect((pantalla().querySelector('#minimo') as HTMLInputElement).value).toBe('5');
    await escribir('minimo', '2');

    await pulsar('Guardar mínimo');
    expect(inventario.minimos).toEqual([{ id: 7, minimo: 2 }]);
    // Lo guardado queda en el campo: no hay cambios sin guardar
    expect((pantalla().querySelector('#minimo') as HTMLInputElement).value).toBe('5');
    expect((harness.routeDebugElement!.componentInstance as StockPage).tieneCambiosSinGuardar()).toBe(false);

    await pulsar('Quitar mínimo');
    expect(inventario.minimos[1]).toEqual({ id: 7, minimo: null });
    expect(texto()).toContain('Mínimo quitado (era 5).');
  });

  // Con ceros de más el mínimo se guarda, no se quita
  it('guarda «5,000» como 5', async () => {
    await abrir();
    await escribir('minimo', '5,000');

    await pulsar('Guardar mínimo');

    expect(inventario.minimos).toEqual([{ id: 7, minimo: 5 }]);
  });

  // HU-08 RF-1: el Contador solo ve
  it('el contador ve el stock y el historial, sin los formularios', async () => {
    await abrir('/inventario/7', 'CONTADOR');

    expect(texto()).toContain('Disponible3 unidad');
    expect(texto()).toContain('Factura 123');
    expect(pantalla().querySelector('form')).toBeNull();
  });

  it('avisa si la dirección no es la de un producto', async () => {
    await abrir('/inventario/abc');

    expect(texto()).toContain('Esa dirección no es la de un producto.');
  });

  // Salir con algo escrito pregunta (cambiosSinGuardarGuard)
  it('sabe si hay cambios sin guardar', async () => {
    await abrir();
    const pagina = harness.routeDebugElement!.componentInstance as StockPage;
    expect(pagina.tieneCambiosSinGuardar()).toBe(false);

    await escribir('entrada-cantidad', '4');

    expect(pagina.tieneCambiosSinGuardar()).toBe(true);
  });
});
