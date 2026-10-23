import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CARGAR_VISOR_3D, cargadorDeVisor, Visor3d } from './visor-3d';

@Component({
  imports: [Visor3d],
  template: `<app-visor-3d [src]="src()" alt="Martillo de peña" />`,
})
class Anfitrion {
  readonly src = signal('/api/productos/7/modelo/a.glb');
}

// HU-15 RF-9: el visor del modelo 3D, sin WebGL de verdad (jsdom no lo tiene)
describe('Visor3d', () => {
  async function abrir(cargar: () => Promise<boolean>) {
    TestBed.configureTestingModule({ providers: [{ provide: CARGAR_VISOR_3D, useValue: cargar }] });
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    return fixture;
  }

  const visor = (elemento: HTMLElement) => elemento.querySelector('model-viewer');
  const texto = (elemento: HTMLElement) => (elemento.textContent ?? '').replace(/\s+/g, ' ');

  // HU-15 RF-9: girar, acercar y mover son los controles de cámara de model-viewer
  it('carga el visor y muestra el modelo', async () => {
    const cargar = vi.fn(() => Promise.resolve(true));
    const fixture = await abrir(cargar);
    const elemento = fixture.nativeElement as HTMLElement;

    expect(cargar).toHaveBeenCalledTimes(1);
    const modelo = visor(elemento)!;
    expect(modelo.getAttribute('src')).toBe('/api/productos/7/modelo/a.glb');
    expect(modelo.getAttribute('alt')).toBe('Martillo de peña');
    expect(modelo.hasAttribute('camera-controls')).toBe(true);
    expect(texto(elemento)).toContain('Cargando el modelo 3D…');

    modelo.dispatchEvent(new Event('load'));
    await fixture.whenStable();
    expect(texto(elemento)).not.toContain('Cargando');

    // Otro modelo (se reemplazó): vuelve a decir que carga
    fixture.componentInstance.src.set('/api/productos/7/modelo/b.glb');
    await fixture.whenStable();
    expect(visor(elemento)!.getAttribute('src')).toBe('/api/productos/7/modelo/b.glb');
    expect(texto(elemento)).toContain('Cargando el modelo 3D…');
  });

  // HU-15 caso límite: un celular sin WebGL
  it('sin WebGL lo dice en lugar del visor', async () => {
    const fixture = await abrir(() => Promise.resolve(false));
    expect(visor(fixture.nativeElement)).toBeNull();
    expect(texto(fixture.nativeElement)).toContain('No pudimos mostrar el modelo en este dispositivo.');
  });

  // HU-15 RF-9: un archivo que el visor no puede mostrar
  it('si el modelo no se puede mostrar lo dice', async () => {
    const fixture = await abrir(() => Promise.resolve(true));
    visor(fixture.nativeElement)!.dispatchEvent(new Event('error'));
    await fixture.whenStable();
    expect(visor(fixture.nativeElement)).toBeNull();
    // Lo anuncia la misma región que decía «Cargando…» (/code-review, ronda 2)
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain(
      'No pudimos mostrar el modelo en este dispositivo.',
    );
  });

  // Sin red no se descarga el visor
  it('si el visor no se descarga lo dice', async () => {
    const fixture = await abrir(() => Promise.reject(new Error('sin red')));
    expect(texto(fixture.nativeElement)).toContain('No pudimos mostrar el modelo en este dispositivo.');
  });

  // /code-review, ronda 2: con otro modelo (se reemplazó) vuelve a intentar la descarga
  it('con otro modelo vuelve a intentar la descarga', async () => {
    const cargar = vi.fn().mockRejectedValueOnce(new Error('sin red')).mockResolvedValue(true);
    const fixture = await abrir(cargar);
    expect(visor(fixture.nativeElement)).toBeNull();

    fixture.componentInstance.src.set('/api/productos/7/modelo/b.glb');
    await fixture.whenStable();

    expect(cargar).toHaveBeenCalledTimes(2);
    expect(visor(fixture.nativeElement)?.getAttribute('src')).toBe('/api/productos/7/modelo/b.glb');
  });

  // /code-review: la respuesta se recuerda, pero un fallo de la descarga se vuelve a intentar
  it('recuerda el visor y reintenta si la descarga falló', async () => {
    const importar = vi.fn().mockRejectedValueOnce(new Error('sin red')).mockResolvedValue({});
    const cargar = cargadorDeVisor(importar, () => true);

    await expect(cargar()).rejects.toThrow('sin red');
    await expect(cargar()).resolves.toBe(true);
    await expect(cargar()).resolves.toBe(true);
    expect(importar).toHaveBeenCalledTimes(2);

    const sinWebgl = vi.fn();
    await expect(cargadorDeVisor(sinWebgl, () => false)()).resolves.toBe(false);
    expect(sinWebgl).not.toHaveBeenCalled();
  });
});
