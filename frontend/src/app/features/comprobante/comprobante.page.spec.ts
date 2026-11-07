import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { VentasService } from '../../core/api/ventas.service';
import { Sesion } from '../../core/modelos/sesion';
import { Rol } from '../../core/modelos/usuario';
import { Venta } from '../../core/modelos/venta';
import { SesionService } from '../../core/sesion/sesion.service';
import { ComprobantePage } from './comprobante.page';

// Como responde GET /api/ventas/{id} (VentaTiendaIntegracionTest)
const EN_EFECTIVO: Venta = {
  idPedido: 31,
  estado: 'ENTREGADO',
  confirmado: '2026-11-05T10:00:00',
  vence: '2026-11-05T10:10:00',
  segundosParaPagar: 0,
  pagado: '2026-11-05T10:01:00',
  comprador: { idUsuario: 4, nombre: 'Ana Paz', correo: 'ana@mail.com' },
  vendidoPor: 'Eva Rojas',
  lineas: [
    { idProducto: 1, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85, cantidad: 1, subtotal: 85 },
    { idProducto: 2, nombre: 'Cable', unidad: 'METRO', precio: 4.5, cantidad: 2.5, subtotal: 11.25 },
  ],
  total: 96.25,
  medio: 'EFECTIVO',
  recibido: 100,
  cambio: 3.75,
  cobro: null,
  anulado: null,
  anuladoPor: null,
  motivoAnulacion: null,
  anulable: true,
};

const ANULADA: Venta = {
  ...EN_EFECTIVO,
  estado: 'ANULADO',
  anulado: '2026-11-05T10:30:00',
  anuladoPor: 'Fabio Cruz',
  motivoAnulacion: 'Producto equivocado',
  anulable: false,
};

function sesionCon(rol: Rol): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: {
      idUsuario: 2,
      nombre: 'Eva',
      apellido: 'Rojas',
      correo: 'eva@mail.com',
      telefono: null,
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
      correoVerificado: true,
    },
  };
}

class VentasFalso {
  id: Signal<number | null> | null = null;
  readonly recurso = {
    value: signal<Venta | undefined>(undefined),
    error: signal<unknown>(undefined),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  readonly anular = vi.fn((_id: number, _motivo: string): Promise<Venta> => Promise.resolve(ANULADA));
  venta(id: Signal<number | null>) {
    this.id = id;
    return this.recurso;
  }
}

// HU-23 RF-15, RF-18 a RF-20: el comprobante y la anulación
describe('ComprobantePage', () => {
  let harness: RouterTestingHarness;
  let ventas: VentasFalso;

  async function abrir(url = '/ventas/31', rol: Rol = 'ADMINISTRADOR', datos: Venta | undefined = EN_EFECTIVO) {
    localStorage.clear();
    TestBed.resetTestingModule();
    ventas = new VentasFalso();
    ventas.recurso.value.set(datos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'ventas/:id', component: ComprobantePage },
          { path: 'ventas/nueva', children: [] },
          { path: 'pedidos', children: [] },
          { path: 'pedidos/:id', children: [] },
        ]),
        { provide: VentasService, useValue: ventas },
      ],
    });
    TestBed.inject(SesionService).iniciar(sesionCon(rol));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  const pantalla = (): HTMLElement => harness.routeNativeElement!;
  const ticket = (): string => pantalla().querySelector('.ticket')?.textContent?.replace(/\s+/g, ' ') ?? '';
  // Una fila del pie de la tabla: su rótulo y su valor
  const fila = (rotulo: string): string | undefined =>
    Array.from(pantalla().querySelectorAll('tfoot tr'))
      .find((cada) => cada.querySelector('th')?.textContent?.trim() === rotulo)
      ?.querySelector('td')
      ?.textContent?.replace(/\s+/g, ' ')
      .trim();
  // Los de arriba del ticket: el diálogo tiene su propio «Anular venta»
  const boton = (texto: string): HTMLButtonElement | undefined =>
    Array.from(pantalla().querySelectorAll<HTMLButtonElement>('.acciones button')).find(
      (cada) => cada.textContent?.trim() === texto,
    );

  // HU-23 RF-15: la marca, el número, el comprador, las líneas, el total, el medio y la leyenda
  it('muestraElTicketCompleto', async () => {
    await abrir();

    expect(ventas.id!()).toBe(31);
    expect(ticket()).toContain('Tienda Montero');
    expect(ticket()).toContain('Venta N.º 31');
    expect(ticket()).toContain('05/11/2026 10:01');
    expect(ticket()).toContain('Ana Paz');
    expect(ticket()).toContain('Eva Rojas');
    expect(ticket()).toContain('2,5 metros × Bs 4,50');
    expect(ticket()).toContain('Bs 96,25');
    expect(ticket()).toContain('No válido como factura');
    const imprimir = vi.spyOn(window, 'print').mockImplementation(() => undefined);
    boton('Imprimir')!.click();
    expect(imprimir).toHaveBeenCalled();
  });

  // HU-23 RF-15: en efectivo, lo recibido y el cambio; sin cuenta, «Consumidor final»
  it('enEfectivoMuestraRecibidoYCambio', async () => {
    await abrir('/ventas/31', 'EMPLEADO', { ...EN_EFECTIVO, comprador: null });

    expect(ticket()).toContain('Consumidor final');
    expect(fila('Pago')).toBe('Efectivo');
    expect(fila('Recibido')).toBe('Bs 100,00');
    expect(fila('Cambio')).toBe('Bs 3,75');
  });

  // HU-23 RF-18, RF-19: pide el motivo, y la venta queda «Anulado» con quién y por qué
  it('anularPideMotivoYMarcaAnulada', async () => {
    await abrir();
    const dialogo = pantalla().querySelector('dialog')!;
    dialogo.showModal = vi.fn();
    dialogo.close = vi.fn();

    boton('Anular venta')!.click();
    await harness.fixture.whenStable();
    expect(dialogo.showModal).toHaveBeenCalled();

    // Sin motivo no anula
    pantalla().querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();
    expect(pantalla().querySelector('#error-motivo')?.textContent).toContain('Escriba por qué');
    expect(ventas.anular).not.toHaveBeenCalled();

    const motivo = pantalla().querySelector<HTMLTextAreaElement>('#motivo')!;
    motivo.value = '  Producto equivocado ';
    motivo.dispatchEvent(new Event('input'));
    pantalla().querySelector<HTMLFormElement>('dialog form')!.dispatchEvent(new Event('submit'));
    await harness.fixture.whenStable();

    expect(ventas.anular).toHaveBeenCalledWith(31, 'Producto equivocado');
    expect(ticket()).toContain('Anulado');
    expect(ticket()).toContain('Anulada el 05/11/2026 10:30 por Fabio Cruz: Producto equivocado');
    expect(boton('Anular venta')).toBeUndefined();
    // Sin el botón, el foco va al título del ticket y no se pierde (revisión de interfaz)
    expect(document.activeElement?.id).toBe('titulo-ticket');
  });

  // HU-23 RF-20: el Empleado no ve «Anular venta», ni el Administrador en una que ya no se puede
  it('sinAnularParaElEmpleado', async () => {
    await abrir('/ventas/31', 'EMPLEADO');
    expect(boton('Anular venta')).toBeUndefined();

    await abrir('/ventas/31', 'ADMINISTRADOR', { ...EN_EFECTIVO, anulable: false });
    expect(boton('Anular venta')).toBeUndefined();
  });

  // HU-23 (casos límite): un id que no es un número no pide nada; uno de un pedido en línea es un 404
  it('idBasuraOEnLinea', async () => {
    await abrir('/ventas/abc', 'EMPLEADO', undefined);
    expect(ventas.id!()).toBeNull();
    expect(pantalla().textContent).toContain('No encontramos esa venta');

    await abrir('/ventas/12', 'EMPLEADO', undefined);
    ventas.recurso.error.set(
      new HttpErrorResponse({ status: 404, error: { status: 404, detail: 'x', codigo: 'VENTA_NO_ENCONTRADA' } }),
    );
    await harness.fixture.whenStable();
    expect(pantalla().textContent).toContain('No encontramos esa venta');
    expect(pantalla().querySelector('a[href="/pedidos"]')).not.toBeNull();
  });
});
