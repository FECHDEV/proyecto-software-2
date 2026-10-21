import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CarritoService } from '../../core/api/carrito.service';
import { PedidosService } from '../../core/api/pedidos.service';
import { Carrito, LineaDeCarrito } from '../../core/modelos/carrito';
import { ErrorApi } from '../../core/modelos/error-api';
import { DatosDeConfirmacion, Pedido, Retiro } from '../../core/modelos/pedido';
import { Usuario } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { ConfirmacionPage } from './confirmacion.page';

function linea(cambios: Partial<LineaDeCarrito>): LineaDeCarrito {
  return {
    idProducto: 7,
    nombre: 'Martillo',
    imagen: null,
    precio: 85.5,
    unidad: 'UNIDAD',
    cantidad: 2,
    subtotal: 171,
    estado: 'DISPONIBLE',
    ...cambios,
  };
}

const CON_DOS: Carrito = {
  lineas: [linea({}), linea({ idProducto: 9, nombre: 'Cable', precio: 12, unidad: 'METRO', cantidad: 2.5, subtotal: 30 })],
  total: 201,
  cantidadLineas: 2,
  idPedidoPendiente: null,
  correoVerificado: true,
};

const ANA: Usuario = {
  idUsuario: 1,
  nombre: 'Ana',
  apellido: 'Paz',
  correo: 'ana@mail.com',
  telefono: '70000000',
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-10-17T09:00:00',
  correoVerificado: true,
};

const PEDIDO = { idPedido: 12 } as Pedido;

function rechazo(codigo: string, extra: Partial<ErrorApi> = {}): ErrorApi {
  return { status: 409, detail: 'rechazo', codigo, ...extra };
}

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
}

class PedidosFalso {
  readonly datosDeRetiro = signal<Retiro | undefined>({ direccion: null, horario: null });
  readonly confirmar = vi.fn((_datos: DatosDeConfirmacion): Promise<Pedido> => Promise.resolve(PEDIDO));
  readonly errorDeRetiro = signal<unknown>(undefined);
  readonly recargarRetiro = vi.fn(() => true);
  retiro() {
    const valor = this.datosDeRetiro;
    return {
      value: valor,
      hasValue: () => valor() !== undefined,
      error: this.errorDeRetiro,
      reload: this.recargarRetiro,
    };
  }
}

// HU-12: la confirmación del pedido
describe('ConfirmacionPage', () => {
  let harness: RouterTestingHarness;
  let carrito: CarritoFalso;
  let pedidos: PedidosFalso;
  let sesionUsuario: Usuario = ANA;

  beforeEach(() => (sesionUsuario = ANA));

  async function abrir(contenido: Carrito = CON_DOS, retiro: Retiro = { direccion: null, horario: null }): Promise<void> {
    TestBed.resetTestingModule();
    carrito = new CarritoFalso();
    carrito.carrito.value.set(contenido);
    pedidos = new PedidosFalso();
    pedidos.datosDeRetiro.set(retiro);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'confirmar-pedido', component: ConfirmacionPage },
          { path: 'carrito', children: [] },
          { path: 'mis-pedidos/:id', children: [] },
        ]),
        { provide: CarritoService, useValue: carrito },
        { provide: PedidosService, useValue: pedidos },
        { provide: SesionService, useValue: { usuario: signal(sesionUsuario), rol: () => 'CLIENTE' } },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/confirmar-pedido');
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  // El formato de precios separa «Bs» con un espacio duro
  function texto(elemento: Element | null = pantalla()): string {
    return (elemento?.textContent ?? '').replace(/\s+/g, ' ');
  }

  function campo(id: string): HTMLInputElement {
    return pantalla().querySelector<HTMLInputElement>(`#${id}`)!;
  }

  async function escribir(id: string, valor: string): Promise<void> {
    campo(id).value = valor;
    campo(id).dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
  }

  async function confirmar(): Promise<void> {
    pantalla().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await harness.fixture.whenStable();
    // El foco y la navegación llegan después de que termina el envío
    await new Promise((listo) => setTimeout(listo));
    await harness.fixture.whenStable();
  }

  function fila(nombre: string): HTMLElement {
    return Array.from(pantalla().querySelectorAll<HTMLElement>('.linea')).find((cada) =>
      cada.textContent?.includes(nombre),
    )!;
  }

  // HU-12 RF-1
  it('muestra las líneas, el total y los datos de la cuenta', async () => {
    await abrir();

    expect(texto(fila('Martillo'))).toContain('Bs 171,00');
    expect(texto(fila('Cable'))).toContain('Bs 30,00');
    expect(texto(pantalla().querySelector('.total'))).toContain('Bs 201,00');
    expect(texto()).toContain('Se retira en la tienda');
    expect(campo('nombreRetira').value).toBe('Ana Paz');
    expect(campo('telefonoRetira').value).toBe('70000000');
    expect(campo('nota').value).toBe('');
    // Precio y disponibilidad de hoy al entrar
    expect(carrito.carrito.reload).toHaveBeenCalled();
  });

  // HU-12 RF-1, RF-2: otra persona puede retirar
  it('confirma con lo escrito y el total que ve, y lleva al pedido', async () => {
    await abrir();
    await escribir('nombreRetira', 'Luis Rojas');
    await escribir('telefonoRetira', '71234567');
    await escribir('nota', 'Retira mi hermano');

    await confirmar();

    expect(pedidos.confirmar).toHaveBeenCalledWith({
      nombreRetira: 'Luis Rojas',
      telefonoRetira: '71234567',
      nota: 'Retira mi hermano',
      totalEsperado: 201,
    });
    expect(TestBed.inject(Router).url).toBe('/mis-pedidos/12');
  });

  // HU-12 RF-3
  it('marca las líneas sin stock y enlaza al carrito', async () => {
    await abrir();
    pedidos.confirmar.mockRejectedValueOnce(
      rechazo('STOCK_INSUFICIENTE', { lineas: [{ idProducto: 7, motivo: 'NO_ALCANZA' }] }),
    );

    await confirmar();

    expect(texto(fila('Martillo'))).toContain('No hay stock suficiente');
    expect(texto(fila('Cable'))).not.toContain('stock');
    const aviso = pantalla().querySelector('[role="alert"]');
    expect(texto(aviso)).toContain('Corríjalos en el carrito');
    expect(aviso?.querySelector('a')?.getAttribute('href')).toBe('/carrito');
    expect(TestBed.inject(Router).url).toBe('/confirmar-pedido');
    // Revisión de HU-12 (interfaz): el foco va al aviso, que lleva al enlace para seguir
    expect(document.activeElement).toBe(aviso);
  });

  // Revisión de HU-12 (interfaz): nombre y apellido de 80 cada uno no pasan del límite de 160
  it('el nombre de la cuenta se precarga dentro del límite', async () => {
    sesionUsuario = { ...ANA, nombre: 'n'.repeat(80), apellido: 'a'.repeat(80) };
    await abrir();

    expect(campo('nombreRetira').value.length).toBe(160);
    await confirmar();
    expect(pedidos.confirmar).toHaveBeenCalled();
  });

  // HU-12 RF-6
  it('valida el nombre y el teléfono de quien retira, sin contar los espacios', async () => {
    await abrir();
    await escribir('nombreRetira', '   ');
    await escribir('telefonoRetira', '1'.repeat(21));

    await confirmar();

    expect(pedidos.confirmar).not.toHaveBeenCalled();
    expect(texto()).toContain('Escriba el nombre de quien retira.');
    expect(texto()).toContain('El teléfono admite hasta 20 caracteres.');
    expect(document.activeElement?.id).toBe('nombreRetira');

    await escribir('nombreRetira', `  ${'a'.repeat(160)}  `);
    await escribir('telefonoRetira', ' 70000000 ');
    await confirmar();
    expect(pedidos.confirmar).toHaveBeenCalled();
  });

  // HU-12 RF-15
  it('un total cambiado recarga el carrito y dice el total nuevo', async () => {
    await abrir();
    carrito.carrito.reload.mockClear();
    pedidos.confirmar.mockRejectedValueOnce(rechazo('TOTAL_CAMBIADO', { total: 210 }));

    await confirmar();

    const aviso = texto(pantalla().querySelector('[role="alert"]'));
    expect(aviso).toContain('Bs 210,00');
    // Revisión de HU-12 (/code-review): no siempre es un precio; también pudo volver un pedido vencido
    expect(aviso).toContain('El total cambió');
    expect(aviso).not.toContain('precio');
    expect(carrito.carrito.reload).toHaveBeenCalled();
    expect(TestBed.inject(Router).url).toBe('/confirmar-pedido');
  });

  // Revisión de HU-12 (/code-review): mientras el carrito recarga, el total que se ve puede ser el viejo
  it('no confirma mientras el carrito recarga', async () => {
    await abrir();
    carrito.carrito.isLoading.set(true);

    await confirmar();

    expect(pedidos.confirmar).not.toHaveBeenCalled();
    // Y lo dice: el toque no queda sin respuesta
    expect(texto(pantalla().querySelector('button[type="submit"]'))).toContain('Actualizando…');
  });

  // HU-12 RF-5
  it('con un pedido pendiente no ofrece confirmar y enlaza a él', async () => {
    await abrir({ ...CON_DOS, idPedidoPendiente: 5 });

    expect(texto()).toContain('Tiene un pedido esperando el pago');
    expect(pantalla().querySelector('a[href="/mis-pedidos/5"]')).not.toBeNull();
    expect(pantalla().querySelector('form')).toBeNull();
  });

  // HU-20 RF-6, RF-7: abierta a mano, sin el correo verificado no ofrece confirmar
  it('sin el correo verificado no muestra el formulario y lleva al carrito', async () => {
    await abrir({ ...CON_DOS, correoVerificado: false });

    expect(pantalla().querySelector('form')).toBeNull();
    expect(texto()).toContain('Verifique su correo para confirmar');
    expect(pantalla().querySelector('a[href="/carrito"]')).not.toBeNull();
  });

  // HU-20 RF-6: el backend lo rechaza igual; el carrito se pide de nuevo y queda el aviso
  it('el rechazo por el correo sin verificar lo avisa y recarga el carrito', async () => {
    await abrir();
    carrito.carrito.reload.mockClear();
    pedidos.confirmar.mockRejectedValueOnce(rechazo('CORREO_SIN_VERIFICAR', { status: 403 }));

    await confirmar();

    expect(texto(pantalla().querySelector('[role="alert"]'))).toContain('Verifique su correo');
    expect(carrito.carrito.reload).toHaveBeenCalled();
  });

  // HU-12 RF-5: si el pedido se confirmó en otra pestaña, el rechazo también enlaza
  it('el rechazo por un pedido pendiente enlaza a ese pedido', async () => {
    await abrir();
    pedidos.confirmar.mockRejectedValueOnce(rechazo('PEDIDO_PENDIENTE', { idPedido: 5 }));

    await confirmar();

    expect(pantalla().querySelector('[role="alert"] a')?.getAttribute('href')).toBe('/mis-pedidos/5');
  });

  // HU-12 RF-3, RF-4
  it('sin líneas o con alguna que no se puede comprar, manda al carrito', async () => {
    await abrir({ ...CON_DOS, lineas: [linea({ estado: 'AGOTADO', subtotal: null })], total: 0 });
    expect(pantalla().querySelector('form')).toBeNull();
    expect(pantalla().querySelector('a[href="/carrito"]')).not.toBeNull();

    await abrir({ lineas: [], total: 0, cantidadLineas: 0, idPedidoPendiente: null, correoVerificado: true });
    expect(pantalla().querySelector('form')).toBeNull();
    expect(texto()).toContain('Su carrito está vacío');
  });

  // HU-12 RF-17
  it('muestra dónde y cuándo retirar solo si está configurado', async () => {
    await abrir(CON_DOS, { direccion: 'Av. Principal 123, Montero', horario: 'Lunes a sábado, de 8:00 a 18:00' });
    expect(texto()).toContain('Av. Principal 123, Montero');
    expect(texto()).toContain('Lunes a sábado, de 8:00 a 18:00');

    await abrir(CON_DOS, { direccion: null, horario: null });
    expect(texto()).not.toContain('Dirección');
    expect(texto()).not.toContain('Horario');
  });

  // Auditoría (20/10/2026): si no carga dónde retirar, lo dice en vez de dejar el título solo
  it('si no carga dónde retirar lo avisa y deja reintentar', async () => {
    await abrir();
    pedidos.datosDeRetiro.set(undefined);
    pedidos.errorDeRetiro.set(new Error('sin conexión'));
    await harness.fixture.whenStable();

    expect(harness.routeNativeElement?.textContent).toContain('No pudimos cargar dónde y cuándo retirar.');
    const reintentar = Array.from(harness.routeNativeElement!.querySelectorAll('button')).find(
      (boton) => boton.textContent?.trim() === 'Reintentar',
    )!;
    reintentar.click();
    expect(pedidos.recargarRetiro).toHaveBeenCalled();
  });
});
