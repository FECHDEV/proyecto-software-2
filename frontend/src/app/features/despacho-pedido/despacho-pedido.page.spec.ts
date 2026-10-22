import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { DespachoService } from '../../core/api/despacho.service';
import { FichaDePedido } from '../../core/modelos/despacho';
import { Sesion } from '../../core/modelos/sesion';
import { Rol } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { DespachoPedidoPage } from './despacho-pedido.page';

const PAGADO: FichaDePedido = {
  idPedido: 12,
  estado: 'PAGADO',
  lineas: [
    { idProducto: 7, nombre: 'Martillo', unidad: 'UNIDAD', precio: 85.5, cantidad: 2, subtotal: 171 },
    { idProducto: 9, nombre: 'Cable', unidad: 'METRO', precio: 12, cantidad: 3.5, subtotal: 42 },
  ],
  total: 213,
  nombreRetira: 'José Pérez',
  telefonoRetira: '71234567',
  nota: 'Paso a la tarde',
  confirmado: '2026-10-21T10:00:00',
  pagado: '2026-10-21T10:05:00',
  entregado: null,
  entregadoPor: null,
  clienteNombre: 'Ana Paz',
  clienteCorreo: 'ana@mail.com',
};

const ENTREGADO: FichaDePedido = {
  ...PAGADO,
  estado: 'ENTREGADO',
  entregado: '2026-10-21T16:40:00',
  entregadoPor: 'Eva Rojas',
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

class DespachoFalso {
  id: Signal<number | null> | null = null;
  readonly recurso = {
    value: signal<FichaDePedido | undefined>(undefined),
    error: signal<unknown>(undefined),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  readonly entregar = vi.fn((_id: number): Promise<FichaDePedido> => Promise.resolve(ENTREGADO));
  ficha(id: Signal<number | null>) {
    this.id = id;
    return this.recurso;
  }
}

// HU-17: la ficha de un pedido para el personal y la entrega
describe('DespachoPedidoPage', () => {
  let harness: RouterTestingHarness;
  let despacho: DespachoFalso;

  async function abrir(
    url = '/pedidos/12',
    rol: Rol = 'EMPLEADO',
    configurar: (falso: DespachoFalso) => void = () => {},
  ): Promise<void> {
    localStorage.clear();
    TestBed.resetTestingModule();
    despacho = new DespachoFalso();
    despacho.recurso.value.set(PAGADO);
    configurar(despacho);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'pedidos/:id', component: DespachoPedidoPage },
          { path: 'pedidos', children: [] },
        ]),
        { provide: DespachoService, useValue: despacho },
      ],
    });
    TestBed.inject(SesionService).iniciar(sesionCon(rol));
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  afterEach(() => localStorage.clear());

  const pantalla = () => harness.routeNativeElement as HTMLElement;
  const texto = () => (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  const dialogo = () => pantalla().querySelector('dialog')!;

  function botonEntregar(): HTMLButtonElement | undefined {
    return Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === 'Registrar entrega' && cada.closest('dialog') === null,
    );
  }

  async function esperar(): Promise<void> {
    await harness.fixture.whenStable();
    await harness.fixture.whenStable();
    await new Promise((listo) => setTimeout(listo));
    await harness.fixture.whenStable();
  }

  async function entregarConfirmando(): Promise<void> {
    botonEntregar()!.click();
    await harness.fixture.whenStable();
    (dialogo().querySelector('button.principal') as HTMLButtonElement).click();
    await esperar();
  }

  // HU-17 RF-4
  it('muestra los datos del pedido y del cliente', async () => {
    await abrir();

    expect(despacho.id!()).toBe(12);
    expect(texto()).toContain('Pedido N.º 12');
    expect(pantalla().querySelector('.estado')?.textContent?.trim()).toBe('Pagado');
    expect(texto()).toContain('José Pérez');
    expect(texto()).toContain('71234567');
    expect(texto()).toContain('Paso a la tarde');
    expect(texto()).toContain('Ana Paz');
    expect(texto()).toContain('ana@mail.com');
    expect(texto()).toContain('Martillo');
    expect(texto()).toContain('3,5');
    expect(texto()).toContain('Bs 213,00');
    expect(texto()).toContain('21/10/2026');
    expect(pantalla().querySelector('a[href="/pedidos"]')).not.toBeNull();
  });

  // HU-17 RF-4
  it('entregado dice quién y cuándo', async () => {
    await abrir('/pedidos/12', 'EMPLEADO', (falso) => falso.recurso.value.set(ENTREGADO));

    expect(pantalla().querySelector('.estado')?.textContent?.trim()).toBe('Entregado');
    expect(texto()).toContain('Entregado el 21/10/2026 a las 16:40 por Eva Rojas.');
  });

  // HU-17 RF-5: el diálogo muestra el número, el nombre y el teléfono de quien retira
  it('Registrar entrega pide confirmar con nombre y teléfono', async () => {
    await abrir();

    botonEntregar()!.click();
    await harness.fixture.whenStable();

    const mensaje = (dialogo().textContent ?? '').replace(/\s+/g, ' ');
    expect(mensaje).toContain('Pedido N.º 12');
    expect(mensaje).toContain('José Pérez');
    expect(mensaje).toContain('71234567');
    (dialogo().querySelector('button.secundario') as HTMLButtonElement).click();
    await esperar();
    expect(despacho.entregar).not.toHaveBeenCalled();
  });

  // HU-17 RF-5, RF-6
  it('confirmar lo deja Entregado', async () => {
    await abrir();

    await entregarConfirmando();

    expect(despacho.entregar).toHaveBeenCalledWith(12);
    expect(pantalla().querySelector('.estado')?.textContent?.trim()).toBe('Entregado');
    expect(texto()).toContain('Entregado el 21/10/2026 a las 16:40 por Eva Rojas.');
    expect(botonEntregar()).toBeUndefined();
    // El botón ya no está: el foco va al resultado
    expect(document.activeElement?.classList.contains('entregado')).toBe(true);
  });

  // Caso borde: mientras entrega, el botón no manda otra vez
  it('no se envía dos veces mientras entrega', async () => {
    let responder: (ficha: FichaDePedido) => void = () => {};
    await abrir('/pedidos/12', 'EMPLEADO', (falso) =>
      falso.entregar.mockImplementation(() => new Promise((listo) => (responder = listo))),
    );

    await entregarConfirmando();
    expect(botonEntregar()?.getAttribute('aria-disabled')).toBe('true');
    expect(botonEntregar()?.textContent?.trim()).toBe('Registrar entrega');
    botonEntregar()!.click();
    await harness.fixture.whenStable();
    expect(dialogo().open).toBe(false);

    responder(ENTREGADO);
    await esperar();
    expect(despacho.entregar).toHaveBeenCalledTimes(1);
  });

  // HU-17 RF-7: otro lo entregó antes: lo dice y pide la ficha de nuevo
  it('con 409 se pone al día y lo avisa', async () => {
    await abrir('/pedidos/12', 'EMPLEADO', (falso) =>
      falso.entregar.mockRejectedValue({ status: 409, detail: 'x', codigo: 'PEDIDO_NO_ENTREGABLE' }),
    );

    await entregarConfirmando();

    expect(texto()).toContain('Este pedido ya no se puede entregar: otra persona lo entregó o cambió de estado.');
    expect(despacho.recurso.reload).toHaveBeenCalled();
    expect(document.activeElement?.classList.contains('aviso')).toBe(true);
  });

  // HU-17 RF-9: el Contador ve, pero no entrega
  it('el Contador no ve Registrar entrega', async () => {
    await abrir('/pedidos/12', 'CONTADOR');

    expect(texto()).toContain('José Pérez');
    expect(botonEntregar()).toBeUndefined();
  });

  // HU-17 RF-8: un entregado, o cualquier otro estado que no es Pagado, no ofrece nada
  it('un entregado no ofrece nada', async () => {
    for (const estado of ['ENTREGADO', 'PENDIENTE_PAGO', 'CANCELADO', 'PAGO_A_DEVOLVER'] as const) {
      await abrir('/pedidos/12', 'ADMINISTRADOR', (falso) => falso.recurso.value.set({ ...ENTREGADO, estado }));
      expect(botonEntregar()).toBeUndefined();
    }
  });

  // HU-17 RF-12
  it('un pedido que no existe lleva a la lista', async () => {
    await abrir('/pedidos/12', 'EMPLEADO', (falso) => {
      falso.recurso.value.set(undefined);
      falso.recurso.error.set(
        new HttpErrorResponse({ status: 404, error: { status: 404, detail: '', codigo: 'PEDIDO_NO_ENCONTRADO' } }),
      );
    });
    expect(texto()).toContain('No encontramos ese pedido.');
    expect(pantalla().querySelector('a[href="/pedidos"]')?.textContent).toContain('Ver los pedidos');

    await abrir('/pedidos/abc', 'EMPLEADO', (falso) => falso.recurso.value.set(undefined));
    expect(despacho.id!()).toBeNull();
    expect(texto()).toContain('No encontramos ese pedido.');
  });
});
