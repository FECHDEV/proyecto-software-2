import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { DespachoService } from '../../core/api/despacho.service';
import { FiltrosDeDespacho, PedidoEnDespacho } from '../../core/modelos/despacho';
import { Pagina } from '../../core/modelos/pagina';
import { DespachoPage } from './despacho.page';

function fila(cambios: Partial<PedidoEnDespacho>): PedidoEnDespacho {
  return {
    idPedido: 12,
    estado: 'PAGADO',
    confirmado: '2026-10-21T10:00:00',
    pagado: '2026-10-21T10:05:00',
    nombreRetira: 'José Pérez',
    cantidadProductos: 2,
    total: 213,
    ...cambios,
  };
}

// Como responde GET /api/despacho/pedidos (DespachoIntegracionTest)
function pagina(contenido: PedidoEnDespacho[], numero = 0, totalPaginas = 1): Pagina<PedidoEnDespacho> {
  return { contenido, pagina: numero, tamano: 10, totalElementos: contenido.length, totalPaginas };
}

class DespachoFalso {
  filtros: Signal<FiltrosDeDespacho> | null = null;
  readonly recurso = {
    value: signal<Pagina<PedidoEnDespacho> | undefined>(undefined),
    error: signal<unknown>(undefined),
    isLoading: signal(false),
    reload: vi.fn(() => true),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  lista(filtros: Signal<FiltrosDeDespacho>) {
    this.filtros = filtros;
    return this.recurso;
  }
}

// HU-17: la lista de pedidos del personal
describe('DespachoPage', () => {
  let harness: RouterTestingHarness;
  let despacho: DespachoFalso;

  async function abrir(datos: Pagina<PedidoEnDespacho> | undefined, url = '/pedidos'): Promise<void> {
    TestBed.resetTestingModule();
    despacho = new DespachoFalso();
    despacho.recurso.value.set(datos);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'pedidos', component: DespachoPage },
          { path: 'pedidos/:id', children: [] },
        ]),
        { provide: DespachoService, useValue: despacho },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  const pantalla = () => harness.routeNativeElement as HTMLElement;
  const texto = () => (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  const url = () => TestBed.inject(Router).url;
  const selectorDeEstado = () => pantalla().querySelector<HTMLSelectElement>('#filtro-estado')!;

  // HU-17 RF-1
  it('lista los pedidos con quien retira, chip y enlace', async () => {
    await abrir(
      pagina([
        fila({ idPedido: 12, cantidadProductos: 1, total: 85.5 }),
        fila({ idPedido: 15, estado: 'ENTREGADO', nombreRetira: 'María Gómez' }),
      ]),
    );

    const filas = Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('a.fila'));
    expect(filas.map((cada) => cada.getAttribute('href'))).toEqual(['/pedidos/12', '/pedidos/15']);
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Pedido N.º 12');
    expect(filas[0].textContent).toContain('José Pérez');
    expect(filas[0].textContent).toContain('1 producto');
    expect(filas[0].textContent?.replace(/\s+/g, ' ')).toContain('Bs 85,50');
    expect(texto()).toContain('21/10/2026');
    const chips = Array.from(pantalla().querySelectorAll('.estado')).map((chip) => [
      chip.textContent?.trim(),
      ['tono-aviso', 'tono-exito', 'tono-neutro'].find((tono) => chip.classList.contains(tono)),
    ]);
    expect(chips).toEqual([
      ['Pagado', 'tono-exito'],
      ['Entregado', 'tono-neutro'],
    ]);
  });

  // HU-17 RF-2
  it('de entrada filtra Pagado', async () => {
    await abrir(pagina([fila({})]));

    expect(despacho.filtros!()).toEqual({ estado: 'PAGADO', texto: '', pagina: 0 });
    expect(selectorDeEstado().value).toBe('PAGADO');
    const opciones = Array.from(selectorDeEstado().options).map((opcion) => opcion.textContent?.trim());
    expect(opciones).toEqual([
      'Pagado',
      'Entregado',
      'Pendiente de pago',
      'Pago a devolver',
      'Cancelado',
      'Vencido',
      'Todos',
    ]);
  });

  // HU-17 RF-2, RF-3: los filtros van a la dirección y vuelven a la primera página
  it('cambiar el estado o buscar cambia la dirección', async () => {
    await abrir(pagina([fila({})]), '/pedidos?pagina=2');

    selectorDeEstado().value = 'TODOS';
    selectorDeEstado().dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(url()).toBe('/pedidos?estado=TODOS');
    expect(despacho.filtros!()).toEqual({ estado: 'TODOS', texto: '', pagina: 0 });

    vi.useFakeTimers();
    const busqueda = pantalla().querySelector<HTMLInputElement>('input[type="search"]')!;
    busqueda.value = ' josé ';
    busqueda.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    vi.useRealTimers();
    await harness.fixture.whenStable();
    expect(url()).toBe('/pedidos?estado=TODOS&texto=jos%C3%A9');
    expect(busqueda.value).toBe(' josé ');

    selectorDeEstado().value = 'PAGADO';
    selectorDeEstado().dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
    expect(url()).toBe('/pedidos?texto=jos%C3%A9');
  });

  // Revisión de HU-17 (/code-review, ronda 2): un espacio de más no vuelve a la primera página
  it('un espacio de más en la búsqueda no cambia la página', async () => {
    await abrir(pagina([fila({})], 2, 3), '/pedidos?texto=ana&pagina=2');

    vi.useFakeTimers();
    const busqueda = pantalla().querySelector<HTMLInputElement>('input[type="search"]')!;
    busqueda.value = 'ana ';
    busqueda.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    vi.useRealTimers();
    await harness.fixture.whenStable();

    expect(url()).toBe('/pedidos?texto=ana&pagina=2');
  });

  // HU-17 RF-1
  it('sin pedidos lo dice', async () => {
    await abrir(pagina([]));

    expect(texto()).toContain('No hay pedidos con ese filtro.');
    expect(pantalla().querySelector('a.fila')).toBeNull();
  });

  // HU-17 RF-12: lo que no sirve en la dirección se lee como lo más cercano
  it('una dirección que no sirve muestra lo más cercano', async () => {
    await abrir(pagina([fila({})]), '/pedidos?estado=xx&pagina=-3');

    expect(despacho.filtros!()).toEqual({ estado: 'PAGADO', texto: '', pagina: 0 });
    expect(selectorDeEstado().value).toBe('PAGADO');
  });

  // HU-17 RF-12: si el servidor acomodó la página, la dirección muestra la que se ve
  it('si la página pedida no existe, la dirección muestra la que se ve', async () => {
    await abrir(pagina([fila({})], 1, 2), '/pedidos?pagina=9');

    expect(url()).toBe('/pedidos?pagina=1');
  });

  // Caso borde: el error de carga deja reintentar
  it('un error de carga deja reintentar', async () => {
    await abrir(undefined);
    despacho.recurso.error.set({ status: 0 });
    await harness.fixture.whenStable();

    const reintentar = Array.from(pantalla().querySelectorAll('button')).find(
      (boton) => boton.textContent?.trim() === 'Reintentar',
    );
    reintentar!.click();

    expect(despacho.recurso.reload).toHaveBeenCalled();
  });
});
