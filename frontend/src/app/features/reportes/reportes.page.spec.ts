import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { ReportesService } from '../../core/api/reportes.service';
import { pagina, ReportesFalso, ventas } from './reportes-falso.spec-helper';
import { ReportesPage } from './reportes.page';

// HU-18: la pantalla /reportes
describe('ReportesPage', () => {
  let harness: RouterTestingHarness;
  let reportes: ReportesFalso;

  async function abrir(url = '/reportes') {
    TestBed.resetTestingModule();
    reportes = new ReportesFalso();
    reportes.ventasRecurso.value.set(ventas());
    reportes.productosRecurso.value.set(
      pagina(
        [{ idProducto: 1, codigo: null, nombre: 'Taladro', unidad: 'UNIDAD', activo: true, cantidad: 1, importe: 100, margen: null, sinCosto: 100 }],
        0,
        2,
      ),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'reportes', component: ReportesPage }]),
        { provide: ReportesService, useValue: reportes },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  beforeEach(() => {
    // Las 11:00 del 22/10/2026 en Bolivia
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-10-22T15:00:00Z'));
  });

  afterEach(() => vi.useRealTimers());

  const pantalla = () => harness.routeNativeElement as HTMLElement;
  const texto = () => (pantalla().textContent ?? '').replace(/\s+/g, ' ');
  const url = () => TestBed.inject(Router).url;
  const boton = (nombre: string) =>
    Array.from(pantalla().querySelectorAll('button')).find((cada) => cada.textContent?.trim() === nombre)!;
  const enlace = (nombre: string) =>
    Array.from(pantalla().querySelectorAll<HTMLAnchorElement>('.secciones a')).find(
      (cada) => cada.textContent?.trim() === nombre,
    )!;

  async function cambiar(id: string, valor: string) {
    const campo = pantalla().querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  // HU-18 RF-1, RF-2: las cinco secciones; de entrada, el resumen de este mes
  it('muestra las secciones y cambia la dirección', async () => {
    await abrir();

    const secciones = Array.from(pantalla().querySelectorAll('.secciones a')).map((cada) => cada.textContent?.trim());
    expect(secciones).toEqual(['Resumen', 'Productos', 'Sin venderse', 'Clientes', 'Pedidos perdidos']);
    expect(enlace('Resumen').getAttribute('aria-current')).toBe('page');
    expect(reportes.pedidas).toEqual(['ventas']);
    expect(reportes.filtros!()).toMatchObject({ desde: '2026-10-01', hasta: '2026-10-22', tipo: null });

    enlace('Productos').click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/reportes?seccion=productos');
    expect(enlace('Productos').getAttribute('aria-current')).toBe('page');
    expect(reportes.pedidas).toContain('productos');
  });

  // HU-18 RF-2
  it('los atajos del período', async () => {
    await abrir();
    expect(boton('Este mes').getAttribute('aria-pressed')).toBe('true');
    expect(pantalla().querySelector<HTMLInputElement>('#filtro-hasta')!.max).toBe('2026-10-22');

    boton('Mes anterior').click();
    await harness.fixture.whenStable();
    expect(url()).toBe('/reportes?desde=2026-09-01&hasta=2026-09-30');
    expect(boton('Mes anterior').getAttribute('aria-pressed')).toBe('true');

    await cambiar('filtro-desde', '2026-09-15');
    expect(url()).toBe('/reportes?desde=2026-09-15&hasta=2026-09-30');
    expect(pantalla().querySelector('[aria-pressed="true"]')).toBeNull();

    boton('Hoy').click();
    await harness.fixture.whenStable();
    expect(url()).toBe('/reportes?desde=2026-10-22&hasta=2026-10-22');

    // /code-review, ronda 2: pasada la medianoche, «Hoy» es el día nuevo
    vi.setSystemTime(new Date('2026-10-23T05:00:00Z'));
    boton('Hoy').click();
    await harness.fixture.whenStable();
    expect(url()).toContain('desde=2026-10-23&hasta=2026-10-23');
  });

  // HU-18 RF-5: el tipo va a la dirección y vuelve a la primera página
  it('el tipo cambia la dirección', async () => {
    await abrir('/reportes?seccion=productos&pagina=1');

    await cambiar('filtro-tipo', 'EMPRESA');

    expect(url()).toBe('/reportes?seccion=productos&tipo=EMPRESA');
    expect(reportes.filtros!().tipo).toBe('EMPRESA');
  });

  // HU-18 RF-3: un período que no sirve se corrige en la dirección y se avisa
  it('avisa si ajustó el período', async () => {
    await abrir('/reportes?desde=2026-10-10&hasta=2027-01-01');

    expect(url()).toBe('/reportes?desde=2026-10-10&hasta=2026-10-22');
    expect(texto()).toContain('Ajustamos el período al más cercano que se puede consultar.');
  });

  // HU-18 RF-13, RF-16: «Sin venderse» no usa el período ni el tipo; «Clientes», solo el tipo
  it('cada sección muestra solo los filtros que usa', async () => {
    await abrir('/reportes?seccion=sin-venta');
    expect(pantalla().querySelector('#filtro-desde')).toBeNull();
    expect(pantalla().querySelector('#filtro-tipo')).toBeNull();

    await abrir('/reportes?seccion=clientes');
    expect(pantalla().querySelector('#filtro-desde')).toBeNull();
    expect(pantalla().querySelector('#filtro-tipo')).not.toBeNull();
  });

  // HU-18 RF-11: la página de una lista conserva los filtros; otro filtro vuelve a la primera
  it('la página de una lista conserva los filtros', async () => {
    await abrir('/reportes?seccion=productos&tipo=TALLER');

    boton('Siguiente').click();
    await harness.fixture.whenStable();
    expect(url()).toBe('/reportes?seccion=productos&tipo=TALLER&pagina=1');

    boton('Menos vendidos').click();
    await harness.fixture.whenStable();
    expect(url()).toBe('/reportes?seccion=productos&tipo=TALLER&orden=MENOS');
  });
});
