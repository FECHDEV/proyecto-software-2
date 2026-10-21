import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { PedidosService } from '../../core/api/pedidos.service';
import { MisPedidos, PedidoResumen } from '../../core/modelos/pedido';
import { MisPedidosPage } from './mis-pedidos.page';

function resumen(cambios: Partial<PedidoResumen>): PedidoResumen {
  return {
    idPedido: 12,
    estado: 'PAGADO',
    confirmado: '2026-10-19T10:00:00',
    cantidadProductos: 2,
    total: 213,
    segundosParaPagar: 0,
    ...cambios,
  };
}

// Como responde GET /api/pedidos (MisPedidosIntegracionTest)
function lista(contenido: PedidoResumen[], pendiente: PedidoResumen | null = null, pagina = 0, totalPaginas = 1): MisPedidos {
  return {
    pendiente,
    pagina: { contenido, pagina, tamano: 10, totalElementos: contenido.length, totalPaginas },
  };
}

class PedidosFalso {
  pagina: Signal<number> | null = null;
  readonly recurso = {
    value: signal<MisPedidos | undefined>(undefined),
    error: signal<unknown>(undefined),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  misPedidos(pagina: Signal<number>) {
    this.pagina = pagina;
    return this.recurso;
  }
}

// HU-14: «Mis pedidos»
describe('MisPedidosPage', () => {
  let harness: RouterTestingHarness;
  let pedidos: PedidosFalso;

  async function abrir(datos: MisPedidos | undefined, url = '/mis-pedidos'): Promise<void> {
    TestBed.resetTestingModule();
    pedidos = new PedidosFalso();
    pedidos.recurso.value.set(datos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'mis-pedidos', component: MisPedidosPage },
          { path: 'mis-pedidos/:id', children: [] },
          { path: 'catalogo', children: [] },
        ]),
        { provide: PedidosService, useValue: pedidos },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  const pantalla = () => harness.routeNativeElement as HTMLElement;
  // El formato de precios separa «Bs» con un espacio duro
  const texto = () => (pantalla().textContent ?? '').replace(/\s+/g, ' ');

  // HU-14 RF-1, RF-3
  it('lista los pedidos con su estado, total y enlace', async () => {
    await abrir(
      lista([
        resumen({ idPedido: 14, estado: 'PAGO_A_DEVOLVER', cantidadProductos: 1, total: 85.5 }),
        resumen({ idPedido: 13, estado: 'PAGADO' }),
        resumen({ idPedido: 12, estado: 'CANCELADO' }),
      ]),
    );

    const filas = Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a.fila'));
    expect(filas.map((fila) => fila.getAttribute('href'))).toEqual(['/mis-pedidos/14', '/mis-pedidos/13', '/mis-pedidos/12']);
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Pedido N.º 14');
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Bs 85,50');
    expect(filas[0].textContent).toContain('1 producto');
    expect(filas[1].textContent).toContain('2 productos');
    expect(texto()).toContain('19/10/2026');
    const estados = Array.from(pantalla().querySelectorAll('.estado')).map((chip) => [
      chip.textContent?.trim(),
      ['tono-aviso', 'tono-exito', 'tono-neutro'].find((tono) => chip.classList.contains(tono)),
    ]);
    expect(estados).toEqual([
      ['Pago a devolver', 'tono-aviso'],
      ['Pagado', 'tono-exito'],
      ['Cancelado', 'tono-neutro'],
    ]);
  });

  // HU-14 RF-2
  it('el pendiente va arriba con el tiempo y Pagar', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    const pendiente = resumen({ idPedido: 15, estado: 'PENDIENTE_PAGO', segundosParaPagar: 1200 });
    await abrir(lista([pendiente, resumen({ idPedido: 14 })], pendiente));

    const ventana = pantalla().querySelector('.ventana')!;
    expect(ventana.textContent?.replace(/\s+/g, ' ')).toContain('Pedido N.º 15');
    expect(ventana.querySelector('[role="timer"]')?.textContent).toBe('20:00');
    expect(ventana.querySelector('a.principal')?.getAttribute('href')).toBe('/mis-pedidos/15');

    vi.advanceTimersByTime(1000);
    await harness.fixture.whenStable();
    expect(ventana.querySelector('[role="timer"]')?.textContent).toBe('19:59');
  });

  // HU-14 RF-4: al llegar a cero pide la lista una vez
  it('al llegar a cero el pendiente se pide de nuevo una vez', async () => {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    const pendiente = resumen({ idPedido: 15, estado: 'PENDIENTE_PAGO', segundosParaPagar: 1 });
    await abrir(lista([pendiente], pendiente));

    vi.advanceTimersByTime(1000);
    await harness.fixture.whenStable();
    vi.advanceTimersByTime(3000);
    await harness.fixture.whenStable();

    expect(pedidos.recurso.reload).toHaveBeenCalledTimes(1);
  });

  // HU-14 RF-5
  it('sin pedidos lleva al catálogo', async () => {
    await abrir(lista([]));

    expect(texto()).toContain('Todavía no tiene pedidos.');
    expect(pantalla().querySelector('a[href="/catalogo"]')).not.toBeNull();
    expect(pantalla().querySelector('.ventana')).toBeNull();
  });

  // HU-14 RF-1, RF-8: la página va en la dirección
  it('cambia de página por la dirección', async () => {
    await abrir(lista([resumen({ idPedido: 2 })], null, 1, 2), '/mis-pedidos?pagina=1');

    expect(pedidos.pagina!()).toBe(1);
    expect(texto()).toContain('Página 2 de 2');
    const [anterior, siguiente] = Array.from(pantalla().querySelectorAll<HTMLButtonElement>('.paginas button'));
    expect(siguiente.disabled).toBe(true);

    anterior.click();
    await harness.fixture.whenStable();
    expect(pedidos.pagina!()).toBe(0);

    await harness.navigateByUrl('/mis-pedidos?pagina=abc');
    expect(pedidos.pagina!()).toBe(0);
  });

  // Caso borde: un error de carga deja reintentar
  it('un error de carga deja reintentar', async () => {
    await abrir(undefined);
    pedidos.recurso.error.set({ status: 0, detail: '', codigo: 'SIN_CONEXION' });
    await harness.fixture.whenStable();

    expect(texto()).toContain('No pudimos conectar con el servidor.');
    (Array.from(pantalla().querySelectorAll('button')).find((boton) => boton.textContent?.trim() === 'Reintentar') as HTMLButtonElement).click();
    expect(pedidos.recurso.reload).toHaveBeenCalled();
  });
});
