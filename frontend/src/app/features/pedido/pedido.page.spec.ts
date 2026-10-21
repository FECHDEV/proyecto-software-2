import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { CarritoService } from '../../core/api/carrito.service';
import { PedidosService } from '../../core/api/pedidos.service';
import { Cancelacion, Pedido } from '../../core/modelos/pedido';
import { PedidoPage } from './pedido.page';

// Como responde GET /api/pedidos/12 (PedidoIntegracionTest)
const PENDIENTE: Pedido = {
  idPedido: 12,
  estado: 'PENDIENTE_PAGO',
  lineas: [
    { idProducto: 7, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85.5, cantidad: 2, subtotal: 171 },
    { idProducto: 9, nombre: 'Cable', unidad: 'METRO', precio: 12, cantidad: 3.5, subtotal: 42 },
  ],
  total: 213,
  nombreRetira: 'Luis Rojas',
  telefonoRetira: '71234567',
  nota: 'Retira mi hermano',
  confirmado: '2026-10-19T10:00:00',
  vence: '2026-10-19T10:30:00',
  segundosParaPagar: 1795,
  pagado: null,
  cobro: null,
  retiro: { direccion: 'Av. Principal 123, Montero', horario: null },
};

// Con el QR generado (HU-13), como lo devuelve el simulador
const CON_COBRO: Pedido = {
  ...PENDIENTE,
  cobro: { codigoQr: 'SIMULADOR|SIM-1|213.00', urlPasarela: null, simulador: false },
};

class PedidosFalso {
  id: Signal<number | null> | null = null;
  readonly recurso = {
    value: signal<Pedido | undefined>(undefined),
    error: signal<unknown>(undefined),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  readonly cancelar = vi.fn(
    (_id: number): Promise<Cancelacion> =>
      Promise.resolve({ pedido: { ...PENDIENTE, estado: 'CANCELADO', segundosParaPagar: 0 }, lineasQueNoVolvieron: 0 }),
  );
  readonly pagar = vi.fn((_id: number): Promise<Pedido> => Promise.resolve(CON_COBRO));
  readonly consultarPago = vi.fn((_id: number): Promise<Pedido> => Promise.resolve(CON_COBRO));
  readonly simular = vi.fn((_id: number, _resultado: string): Promise<void> => Promise.resolve());
  pedido(id: Signal<number | null>) {
    this.id = id;
    return this.recurso;
  }
}

// HU-12: la página del pedido del Cliente
describe('PedidoPage', () => {
  let harness: RouterTestingHarness;
  let pedidos: PedidosFalso;
  let carrito: { carrito: { reload: ReturnType<typeof vi.fn> } };

  async function abrir(url = '/mis-pedidos/12', configurar: (falso: PedidosFalso) => void = () => {}): Promise<void> {
    TestBed.resetTestingModule();
    pedidos = new PedidosFalso();
    carrito = { carrito: { reload: vi.fn(() => true) } };
    pedidos.recurso.value.set(PENDIENTE);
    configurar(pedidos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'mis-pedidos/:id', component: PedidoPage },
          { path: 'mis-pedidos', children: [] },
          { path: 'carrito', children: [] },
        ]),
        { provide: PedidosService, useValue: pedidos },
        { provide: CarritoService, useValue: carrito },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  // El formato de precios separa «Bs» con un espacio duro
  function texto(): string {
    return (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  }

  function botonCancelar(): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === 'Cancelar pedido' && cada.closest('dialog') === null,
    );
  }

  const dialogo = () => pantalla().querySelector('dialog')!;

  async function cancelarConfirmando(): Promise<void> {
    botonCancelar()!.click();
    await harness.fixture.whenStable();
    (dialogo().querySelector('button.principal') as HTMLButtonElement).click();
    // La respuesta del diálogo y la del servicio son dos promesas seguidas
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
    // El foco llega después de dibujar el resultado
    await new Promise((listo) => setTimeout(listo));
    await harness.fixture.whenStable();
  }

  // HU-12 RF-8, RF-17
  it('muestra el pedido y el tiempo para pagar', async () => {
    await abrir();

    expect(pedidos.id!()).toBe(12);
    expect(texto()).toContain('Pedido N.º 12');
    expect(texto()).toContain('Pendiente de pago');
    expect(texto()).toContain('29:55');
    expect(texto()).toContain('10:30');
    expect(texto()).toContain('Martillo');
    expect(texto()).toContain('3,5 × Bs 12,00 / metro');
    expect(texto()).toContain('Bs 213,00');
    expect(texto()).toContain('Luis Rojas');
    expect(texto()).toContain('71234567');
    expect(texto()).toContain('Retira mi hermano');
    expect(texto()).toContain('Av. Principal 123, Montero');
    expect(texto()).not.toContain('Horario');
    // El contador no se anuncia cada segundo
    expect(pantalla().querySelector('[role="timer"]')?.getAttribute('aria-live')).toBe('off');
  });

  // HU-12 RF-9
  it('cancelar pide confirmación y deja el pedido cancelado', async () => {
    await abrir();

    botonCancelar()!.click();
    await harness.fixture.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(true);
    (dialogo().querySelector('button.secundario') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    expect(pedidos.cancelar).not.toHaveBeenCalled();

    await cancelarConfirmando();

    expect(pedidos.cancelar).toHaveBeenCalledWith(12);
    expect(texto()).toContain('Cancelado');
    expect(texto()).toContain('Los productos volvieron a su carrito.');
    expect(pantalla().querySelector('a[href="/mis-pedidos"]')).not.toBeNull();
    expect(botonCancelar()).toBeUndefined();
    // Revisión de HU-12 (interfaz): el botón ya no está; el foco va al resultado
    expect(document.activeElement).toBe(pantalla().querySelector('.cerrado'));
  });

  // HU-12 RF-16
  it('avisa cuántas líneas no volvieron al carrito', async () => {
    await abrir();
    pedidos.cancelar.mockResolvedValueOnce({
      pedido: { ...PENDIENTE, estado: 'CANCELADO', segundosParaPagar: 0 },
      lineasQueNoVolvieron: 1,
    });

    await cancelarConfirmando();

    expect(texto()).toContain('Los demás productos volvieron a su carrito.');
    expect(texto()).toContain('1 producto no volvió porque su carrito ya tiene 50.');

    // Revisión de HU-12 (/code-review): otro pedido en la misma pantalla no hereda el aviso
    pedidos.recurso.value.set({ ...PENDIENTE, idPedido: 5, estado: 'VENCIDO', segundosParaPagar: 0 });
    await harness.navigateByUrl('/mis-pedidos/5');
    await harness.fixture.whenStable();
    expect(texto()).toContain('Pedido N.º 5');
    expect(texto()).not.toContain('no volvió');
    expect(texto()).toContain('Los productos volvieron a su carrito.');
  });

  // Revisión de HU-12 (/code-review): si no volvió ninguno, no dice «los demás»
  it('si no volvió ningún producto, no dice que volvieron', async () => {
    await abrir();
    pedidos.cancelar.mockResolvedValueOnce({
      pedido: { ...PENDIENTE, estado: 'CANCELADO', segundosParaPagar: 0 },
      lineasQueNoVolvieron: 2,
    });

    await cancelarConfirmando();

    expect(texto()).not.toContain('volvieron a su carrito');
    expect(texto()).toContain('2 productos no volvieron porque su carrito ya tiene 50.');
  });

  // Revisión de HU-12 (/code-review): la respuesta de una cancelación vieja no pisa otro pedido
  it('si ya se abrió otro pedido, la respuesta de la cancelación no lo pisa', async () => {
    await abrir();
    let responder!: (cancelacion: Cancelacion) => void;
    pedidos.cancelar.mockReturnValueOnce(new Promise((listo) => (responder = listo)));
    botonCancelar()!.click();
    await harness.fixture.whenStable();
    (dialogo().querySelector('button.principal') as HTMLButtonElement).click();
    await harness.fixture.whenStable();

    pedidos.recurso.value.set({ ...PENDIENTE, idPedido: 5 });
    await harness.navigateByUrl('/mis-pedidos/5');
    responder({ pedido: { ...PENDIENTE, estado: 'CANCELADO', segundosParaPagar: 0 }, lineasQueNoVolvieron: 1 });
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();

    expect(texto()).toContain('Pedido N.º 5');
    expect(texto()).toContain('Pendiente de pago');
    expect(texto()).not.toContain('no volvió');
  });

  // HU-12 RF-12: se canceló o venció en otra pestaña
  it('si ya no está pendiente al cancelar, lo dice y pide el pedido de nuevo', async () => {
    await abrir();
    pedidos.cancelar.mockRejectedValueOnce({ status: 409, detail: '', codigo: 'PEDIDO_NO_PENDIENTE' });

    await cancelarConfirmando();

    expect(texto()).toContain('Este pedido ya no espera el pago.');
    expect(pedidos.recurso.reload).toHaveBeenCalled();
    expect(document.activeElement).toBe(pantalla().querySelector('.aviso'));
  });

  // HU-12 RF-10
  it('al llegar a cero pide el pedido otra vez', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    await abrir('/mis-pedidos/12', (falso) => falso.recurso.value.set({ ...PENDIENTE, segundosParaPagar: 2 }));
    expect(texto()).toContain('00:02');

    vi.advanceTimersByTime(1000);
    await harness.fixture.whenStable();
    expect(texto()).toContain('00:01');
    expect(pedidos.recurso.reload).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1000);
    await harness.fixture.whenStable();
    expect(pedidos.recurso.reload).toHaveBeenCalledTimes(1);
    // Si todavía responde «Pendiente», reintenta una vez por segundo, no en cada vuelta
    vi.advanceTimersByTime(500);
    await harness.fixture.whenStable();
    expect(pedidos.recurso.reload).toHaveBeenCalledTimes(1);
  });

  // HU-12 RF-10, RF-12
  it('un pedido que no está pendiente no ofrece cancelar ni cuenta el tiempo', async () => {
    await abrir('/mis-pedidos/12', (falso) =>
      falso.recurso.value.set({ ...PENDIENTE, estado: 'VENCIDO', segundosParaPagar: 0 }),
    );

    expect(texto()).toContain('Vencido');
    expect(texto()).toContain('Los productos volvieron a su carrito.');
    expect(pantalla().querySelector('[role="timer"]')).toBeNull();
    expect(botonCancelar()).toBeUndefined();
  });

  // HU-12 RF-13
  it('el pedido de otro o que no existe dice que no se encontró', async () => {
    await abrir('/mis-pedidos/99', (falso) => {
      falso.recurso.value.set(undefined);
      falso.recurso.error.set(new HttpErrorResponse({ status: 404, error: { status: 404, detail: '', codigo: 'PEDIDO_NO_ENCONTRADO' } }));
    });

    expect(texto()).toContain('No encontramos ese pedido.');
    // Revisión de HU-12 (interfaz): con un camino para seguir
    expect(pantalla().querySelector('a[href="/mis-pedidos"]')).not.toBeNull();
  });

  // HU-12 RF-13 (caso límite: datos basura)
  it('una dirección que no es la de un pedido no consulta la API', async () => {
    await abrir('/mis-pedidos/abc', (falso) => falso.recurso.value.set(undefined));

    expect(pedidos.id!()).toBeNull();
    expect(texto()).toContain('No encontramos ese pedido.');
  });
  // --- HU-13: pagar el pedido ---

  function boton(etiqueta: string): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === etiqueta && cada.closest('dialog') === null,
    );
  }

  async function tocar(etiqueta: string): Promise<void> {
    boton(etiqueta)!.click();
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
  }

  const qr = () => pantalla().querySelector('app-codigo-qr svg');

  // HU-13 RF-1
  it('pendiente sin cobro ofrece Pagar con QR y al tocarlo muestra el QR y el enlace', async () => {
    await abrir();
    expect(qr()).toBeNull();
    pedidos.pagar.mockResolvedValueOnce({
      ...CON_COBRO,
      cobro: { codigoQr: 'QR-REAL', urlPasarela: 'https://pagos.test/abc', simulador: false },
    });

    await tocar('Pagar con QR');

    expect(pedidos.pagar).toHaveBeenCalledWith(12);
    expect(qr()?.getAttribute('aria-label')).toBe('Código QR para pagar el pedido N.º 12');
    const enlace = pantalla().querySelector<HTMLAnchorElement>('a[target="_blank"]');
    expect(enlace?.getAttribute('href')).toBe('https://pagos.test/abc');
    expect(enlace?.getAttribute('rel')).toContain('noopener');
    expect(boton('Pagar con QR')).toBeUndefined();
    expect(boton('Simular pago')).toBeUndefined();
  });

  // HU-13 RF-3
  it('si el cobro no se puede generar avisa y deja reintentar', async () => {
    await abrir();
    pedidos.pagar.mockRejectedValueOnce({ status: 503, detail: '', codigo: 'COBRO_NO_DISPONIBLE' });

    await tocar('Pagar con QR');

    expect(texto()).toContain('No pudimos generar el cobro.');
    expect(boton('Pagar con QR')).toBeDefined();
    await tocar('Pagar con QR');
    expect(pedidos.pagar).toHaveBeenCalledTimes(2);
    expect(qr()).not.toBeNull();
  });

  // HU-13 RF-4, RF-5
  it('con el QR consulta cada 5 segundos y deja de consultar al pagar', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    await abrir('/mis-pedidos/12', (falso) => falso.recurso.value.set(CON_COBRO));

    vi.advanceTimersByTime(4999);
    expect(pedidos.consultarPago).not.toHaveBeenCalled();
    const pagado: Pedido = { ...PENDIENTE, estado: 'PAGADO', segundosParaPagar: 0, pagado: '2026-10-19T10:12:30' };
    pedidos.consultarPago.mockResolvedValueOnce(pagado);
    vi.advanceTimersByTime(1);
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();

    expect(pedidos.consultarPago).toHaveBeenCalledWith(12);
    expect(texto()).toContain('Pagado');
    vi.advanceTimersByTime(20000);
    expect(pedidos.consultarPago).toHaveBeenCalledTimes(1);
  });

  // HU-13 RF-4
  it('Ya pagué consulta en el acto y no dos veces', async () => {
    await abrir('/mis-pedidos/12', (falso) => falso.recurso.value.set(CON_COBRO));
    let responder!: (pedido: Pedido) => void;
    pedidos.consultarPago.mockReturnValueOnce(new Promise((listo) => (responder = listo)));

    boton('Ya pagué')!.click();
    boton('Ya pagué')!.click();
    await harness.fixture.whenStable();
    expect(pedidos.consultarPago).toHaveBeenCalledTimes(1);
    expect(boton('Ya pagué')?.getAttribute('aria-disabled')).toBe('true');

    responder(CON_COBRO);
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
    expect(texto()).toContain('Todavía no recibimos el pago');
  });

  // HU-13 RF-5, RF-15
  it('pagado muestra la fecha y dónde retirar, sin contador', async () => {
    await abrir('/mis-pedidos/12', (falso) =>
      falso.recurso.value.set({ ...PENDIENTE, estado: 'PAGADO', segundosParaPagar: 0, pagado: '2026-10-19T10:12:30' }),
    );

    expect(texto()).toContain('Pagado');
    expect(texto()).toContain('Recibimos su pago el 19/10/2026 a las 10:12.');
    expect(texto()).toContain('Ya puede retirar su pedido en la tienda.');
    expect(texto()).toContain('Av. Principal 123, Montero');
    expect(pantalla().querySelector('[role="timer"]')).toBeNull();
    expect(botonCancelar()).toBeUndefined();
    expect(boton('Pagar con QR')).toBeUndefined();
    expect(texto()).not.toContain('volvieron a su carrito');
  });

  // HU-13 RF-6
  it('tras un error vuelve a ofrecer Pagar con QR', async () => {
    await abrir('/mis-pedidos/12', (falso) => falso.recurso.value.set(CON_COBRO));
    pedidos.consultarPago.mockResolvedValueOnce(PENDIENTE);

    await tocar('Ya pagué');

    expect(texto()).toContain('El pago no se pudo completar. Genere otro QR');
    expect(qr()).toBeNull();
    expect(boton('Pagar con QR')).toBeDefined();
  });

  // HU-13 RF-7: con un QR, al llegar a cero no pide el pedido: la consulta de cada 5 segundos pregunta si se pagó,
  // y el servidor lo vence si no. Una sola consulta por vuelta (revisión de HU-13, /code-review)
  it('al llegar a cero con cobro consulta antes de pedirlo', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    await abrir('/mis-pedidos/12', (falso) => falso.recurso.value.set({ ...CON_COBRO, segundosParaPagar: 1 }));

    vi.advanceTimersByTime(1000);
    await harness.fixture.whenStable();
    expect(pedidos.recurso.reload).not.toHaveBeenCalled();

    pedidos.consultarPago.mockResolvedValueOnce({ ...PENDIENTE, estado: 'VENCIDO', segundosParaPagar: 0 });
    vi.advanceTimersByTime(4000);
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();

    expect(pedidos.consultarPago).toHaveBeenCalledTimes(1);
    expect(texto()).toContain('Vencido');
    expect(pedidos.recurso.reload).not.toHaveBeenCalled();
  });

  // HU-13 RF-12
  it('con el simulador muestra los dos botones; sin él, no', async () => {
    await abrir('/mis-pedidos/12', (falso) =>
      falso.recurso.value.set({ ...CON_COBRO, cobro: { ...CON_COBRO.cobro!, simulador: true } }),
    );
    expect(texto()).toContain('Herramienta de pruebas');
    // El sistema de pago todavía no lo dio por pagado: el QR sigue
    pedidos.consultarPago.mockResolvedValueOnce({ ...CON_COBRO, cobro: { ...CON_COBRO.cobro!, simulador: true } });

    await tocar('Simular pago');

    expect(pedidos.simular).toHaveBeenCalledWith(12, 'PAGADO');
    expect(pedidos.consultarPago).toHaveBeenCalledWith(12);
    expect(boton('Simular error')).toBeDefined();

    pedidos.recurso.value.set(CON_COBRO);
    await harness.fixture.whenStable();
    expect(boton('Simular pago')).toBeUndefined();
    expect(texto()).not.toContain('Herramienta de pruebas');
  });

  // HU-13 RF-15
  it('pago a devolver explica que la tienda devuelve el dinero', async () => {
    await abrir('/mis-pedidos/12', (falso) =>
      falso.recurso.value.set({
        ...PENDIENTE,
        estado: 'PAGO_A_DEVOLVER',
        segundosParaPagar: 0,
        pagado: '2026-10-19T11:00:00',
      }),
    );

    expect(texto()).toContain('Pago a devolver');
    expect(texto()).toContain('La tienda le devolverá el dinero.');
    expect(texto()).not.toContain('volvieron a su carrito');
    expect(botonCancelar()).toBeUndefined();
  });

  // HU-13 (caso borde): la respuesta tardía de otro pedido no se muestra
  it('una respuesta de otro pedido no se muestra', async () => {
    await abrir();
    let responder!: (pedido: Pedido) => void;
    pedidos.pagar.mockReturnValueOnce(new Promise((listo) => (responder = listo)));
    boton('Pagar con QR')!.click();
    await harness.fixture.whenStable();

    pedidos.recurso.value.set({ ...PENDIENTE, idPedido: 5 });
    await harness.navigateByUrl('/mis-pedidos/5');
    responder(CON_COBRO);
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();

    expect(texto()).toContain('Pedido N.º 5');
    expect(qr()).toBeNull();
  });

  // Revisión de HU-13 (interfaz): el total a la vista junto al pago, y el foco no se pierde al generar el QR
  it('muestra el total a pagar y al generar el QR el foco pasa a Ya pagué', async () => {
    await abrir();
    expect(pantalla().querySelector('.monto')?.textContent?.replace(/\s+/g, ' ')).toContain('Bs 213,00');
    const pagar = boton('Pagar con QR')!;
    pagar.focus();

    pagar.click();
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
    await new Promise((listo) => setTimeout(listo));
    await harness.fixture.whenStable();

    expect(document.activeElement).toBe(boton('Ya pagué'));
  });

  // Revisión de HU-13 (interfaz): un doble toque no manda dos simulaciones
  it('Simular pago no se manda dos veces', async () => {
    await abrir('/mis-pedidos/12', (falso) =>
      falso.recurso.value.set({ ...CON_COBRO, cobro: { ...CON_COBRO.cobro!, simulador: true } }),
    );
    let responder!: () => void;
    pedidos.simular.mockReturnValueOnce(new Promise<void>((listo) => (responder = listo)));

    boton('Simular pago')!.click();
    boton('Simular pago')!.click();
    await harness.fixture.whenStable();

    expect(pedidos.simular).toHaveBeenCalledTimes(1);
    expect(boton('Simular pago')?.getAttribute('aria-disabled')).toBe('true');
    responder();
  });

  // Revisión de HU-13 (/code-review ronda 2): por el camino que sea, al dejar de esperar el pago el encabezado se
  // pone al día; mientras espera, no
  it('al dejar de esperar el pago recarga el carrito', async () => {
    await abrir();
    pedidos.recurso.value.set(CON_COBRO);
    await harness.fixture.whenStable();
    expect(carrito.carrito.reload).not.toHaveBeenCalled();

    pedidos.recurso.value.set({ ...PENDIENTE, estado: 'VENCIDO', segundosParaPagar: 0 });
    await harness.fixture.whenStable();

    expect(carrito.carrito.reload).toHaveBeenCalledTimes(1);
  });

  // HU-14 RF-6
  it('enlaza a la lista de pedidos', async () => {
    await abrir();

    expect(pantalla().querySelector('a[href="/mis-pedidos"]')?.textContent?.trim()).toBe('Mis pedidos');
  });
});
