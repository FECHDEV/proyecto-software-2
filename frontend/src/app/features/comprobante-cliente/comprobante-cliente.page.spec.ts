import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { PedidosService } from '../../core/api/pedidos.service';
import { Comprobante } from '../../core/modelos/pedido';
import { ComprobanteClientePage } from './comprobante-cliente.page';

// Como responde GET /api/pedidos/{id}/comprobante (ComprobanteIntegracionTest)
const EN_LINEA: Comprobante = {
  idPedido: 12,
  fecha: '2026-11-17T10:30:00',
  cliente: 'Ana Paz',
  vendidoPor: null,
  lineas: [{ idProducto: 1, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85.5, precioNormal: null, descuento: null, descontado: null, cantidad: 2, subtotal: 171 }],
  total: 171,
  medio: null,
  recibido: null,
  cambio: null,
};

const EN_EFECTIVO: Comprobante = {
  ...EN_LINEA,
  idPedido: 31,
  vendidoPor: 'Eva Suárez',
  medio: 'EFECTIVO',
  recibido: 200,
  cambio: 29,
};

class PedidosFalso {
  id: Signal<number | null> | null = null;
  readonly recurso = {
    value: signal<Comprobante | undefined>(undefined),
    error: signal<unknown>(undefined),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  comprobante(id: Signal<number | null>) {
    this.id = id;
    return this.recurso;
  }
}

// HU-14 RF-10 a RF-12: el comprobante del Cliente
describe('ComprobanteClientePage', () => {
  let harness: RouterTestingHarness;
  let pedidos: PedidosFalso;

  async function abrir(url = '/mis-pedidos/12/comprobante', datos: Comprobante | undefined = EN_LINEA) {
    TestBed.resetTestingModule();
    pedidos = new PedidosFalso();
    pedidos.recurso.value.set(datos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'mis-pedidos/:id/comprobante', component: ComprobanteClientePage },
          { path: 'mis-pedidos/:id', children: [] },
          { path: 'mis-pedidos', children: [] },
        ]),
        { provide: PedidosService, useValue: pedidos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  const pantalla = (): HTMLElement => harness.routeNativeElement!;
  const texto = (): string => pantalla().textContent?.replace(/\s+/g, ' ') ?? '';
  const dato = (rotulo: string): string | undefined =>
    Array.from(pantalla().querySelectorAll('.datos div'))
      .find((cada) => cada.querySelector('dt')?.textContent?.trim() === rotulo)
      ?.querySelector('dd')
      ?.textContent?.trim();
  const fila = (rotulo: string): string | undefined =>
    Array.from(pantalla().querySelectorAll('tfoot tr'))
      .find((cada) => cada.querySelector('th')?.textContent?.trim() === rotulo)
      ?.querySelector('td')
      ?.textContent?.replace(/\s+/g, ' ')
      .trim();
  const enlace = (href: string): HTMLAnchorElement | null => pantalla().querySelector(`a[href="${href}"]`);

  it('RF-10: en línea muestra el cliente, las líneas, el total y «Pago: En línea», sin «Atendió» ni chip', async () => {
    await abrir();

    expect(pedidos.id!()).toBe(12);
    expect(pantalla().querySelector('h1')?.textContent?.replace(/\s+/g, ' ')).toContain('Pedido N.º 12');
    expect(texto()).toContain('17/11/2026 10:30');
    expect(dato('Cliente')).toBe('Ana Paz');
    expect(dato('Atendió')).toBeUndefined();
    expect(texto()).toContain('2 unidades × Bs 85,50');
    expect(fila('Total')).toBe('Bs 171,00');
    expect(fila('Pago')).toBe('En línea');
    expect(fila('Recibido')).toBeUndefined();
    expect(pantalla().querySelector('.estado')).toBeNull();
    expect(texto()).toContain('No válido como factura');
    expect(enlace('/mis-pedidos/12')).not.toBeNull();
    expect(enlace('/mis-pedidos')).not.toBeNull();
  });

  it('RF-10: del mostrador en efectivo muestra «Atendió», lo recibido y el cambio', async () => {
    await abrir('/mis-pedidos/31/comprobante', EN_EFECTIVO);

    expect(dato('Atendió')).toBe('Eva Suárez');
    expect(fila('Pago')).toBe('Efectivo');
    expect(fila('Recibido')).toBe('Bs 200,00');
    expect(fila('Cambio')).toBe('Bs 29,00');
  });

  // HU-24 RF-18: el comprobante del Cliente, con el descuento y lo ahorrado
  it('RF-18: el ticket con descuento', async () => {
    await abrir('/mis-pedidos/12/comprobante', {
      ...EN_LINEA,
      lineas: [{ idProducto: 9, nombre: 'Rastrillo', unidad: 'UNIDAD', precio: 85, precioNormal: 100, descuento: 15, descontado: 30, cantidad: 2, subtotal: 170 }],
      total: 170,
    });

    expect(texto()).toContain('2 unidades × Bs 85,00 (−15 %)');
    expect(fila('Usted ahorró')).toBe('Bs 30,00');
  });

  it('RF-11: «Imprimir» llama a window.print', async () => {
    await abrir();
    const imprimir = vi.spyOn(window, 'print').mockImplementation(() => undefined);

    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Imprimir')!
      .click();

    expect(imprimir).toHaveBeenCalled();
    imprimir.mockRestore();
  });

  it('RF-12: un 404 dice que no tiene comprobante, con los enlaces', async () => {
    await abrir('/mis-pedidos/12/comprobante', undefined);
    pedidos.recurso.error.set(
      new HttpErrorResponse({ status: 404, error: { status: 404, detail: 'x', codigo: 'PEDIDO_NO_ENCONTRADO' } }),
    );
    await harness.fixture.whenStable();

    expect(texto()).toContain('Este pedido no tiene comprobante');
    expect(pantalla().querySelector('.ticket')).toBeNull();
    expect(enlace('/mis-pedidos/12')).not.toBeNull();
    expect(enlace('/mis-pedidos')).not.toBeNull();
  });

  it('RF-12: un id que no es un número no pide nada y enlaza solo a Mis pedidos', async () => {
    await abrir('/mis-pedidos/abc/comprobante', undefined);

    expect(pedidos.id!()).toBeNull();
    expect(texto()).toContain('Este pedido no tiene comprobante');
    expect(enlace('/mis-pedidos')).not.toBeNull();
    expect(pantalla().querySelector('a[href^="/mis-pedidos/"]')).toBeNull();
  });

  it('un error de red muestra «Reintentar», que vuelve a pedir', async () => {
    await abrir('/mis-pedidos/12/comprobante', undefined);
    pedidos.recurso.error.set(new HttpErrorResponse({ status: 0 }));
    await harness.fixture.whenStable();

    expect(pantalla().querySelector('[role="alert"]')).not.toBeNull();
    Array.from(pantalla().querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Reintentar')!
      .click();

    expect(pedidos.recurso.reload).toHaveBeenCalled();
  });
});
