import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import {
  CARGAR_VISOR_3D,
  cargadorDeVisor,
  configurarDecodificadores,
  MOVIMIENTO_REDUCIDO,
  Visor3d,
} from './visor-3d';

@Component({
  imports: [Visor3d],
  template: `<app-visor-3d
    [src]="src()"
    alt="Martillo de peña"
    [poster]="poster()"
    [realidadAumentada]="realidadAumentada()"
  />`,
})
class Anfitrion {
  readonly src = signal('/api/productos/7/modelo/a.glb');
  readonly poster = signal<string | null>(null);
  readonly realidadAumentada = signal(false);
}

// HU-15 RF-9: el visor del modelo 3D, sin WebGL de verdad (jsdom no lo tiene)
describe('Visor3d', () => {
  async function abrir(
    cargar: () => Promise<boolean>,
    opciones: { poster?: string; realidadAumentada?: boolean; movimientoReducido?: boolean } = {},
  ) {
    TestBed.configureTestingModule({
      providers: [
        { provide: CARGAR_VISOR_3D, useValue: cargar },
        { provide: MOVIMIENTO_REDUCIDO, useValue: () => opciones.movimientoReducido ?? false },
      ],
    });
    const fixture = TestBed.createComponent(Anfitrion);
    fixture.componentInstance.poster.set(opciones.poster ?? null);
    fixture.componentInstance.realidadAumentada.set(opciones.realidadAumentada ?? false);
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
    // HU-16 RF-5, RF-7: los controles incluyen el teclado (model-viewer), y el modelo no gira solo
    expect(modelo.hasAttribute('auto-rotate')).toBe(false);
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

  // HU-16 RF-8
  it('con realidad aumentada ofrece «Ver en su espacio»', async () => {
    const fixture = await abrir(() => Promise.resolve(true), { realidadAumentada: true });
    const modelo = visor(fixture.nativeElement)!;

    expect(modelo.hasAttribute('ar')).toBe(true);
    expect(modelo.getAttribute('ar-modes')).toBe('webxr scene-viewer quick-look');
    // En tamaño real: la escala no se cambia con los dedos
    expect(modelo.getAttribute('ar-scale')).toBe('fixed');
    // model-viewer lo oculta donde no hay soporte
    const boton = modelo.querySelector('button[slot="ar-button"]')!;
    expect(boton.textContent).toContain('Ver en su espacio');
  });

  // HU-16 RF-8: el formulario de HU-15 sigue sin realidad aumentada
  it('sin realidad aumentada no hay botón ni ar', async () => {
    const fixture = await abrir(() => Promise.resolve(true));
    const modelo = visor(fixture.nativeElement)!;

    expect(modelo.hasAttribute('ar')).toBe(false);
    expect(modelo.hasAttribute('ar-modes')).toBe(false);
    expect(modelo.querySelector('[slot="ar-button"]')).toBeNull();
  });

  // HU-16 RF-4
  it('muestra el póster mientras descarga el visor y lo pasa al modelo', async () => {
    let listo!: (disponible: boolean) => void;
    const fixture = await abrir(() => new Promise((resolver) => (listo = resolver)), { poster: '/api/productos/7/imagenes/a.webp' });
    const elemento = fixture.nativeElement as HTMLElement;

    expect(visor(elemento)).toBeNull();
    expect(elemento.querySelector('img.poster')?.getAttribute('src')).toBe('/api/productos/7/imagenes/a.webp');
    expect(texto(elemento)).toContain('Cargando el modelo 3D…');

    listo(true);
    await fixture.whenStable();
    expect(elemento.querySelector('img.poster')).toBeNull();
    expect(visor(elemento)!.getAttribute('poster')).toBe('/api/productos/7/imagenes/a.webp');
  });

  // HU-16 RF-7
  it('sin movimiento reducido usa la pista animada', async () => {
    const fixture = await abrir(() => Promise.resolve(true));
    const modelo = visor(fixture.nativeElement)!;
    modelo.dispatchEvent(new Event('load'));
    await fixture.whenStable();

    expect(modelo.getAttribute('interaction-prompt')).toBe('auto');
    expect(texto(fixture.nativeElement)).not.toContain('Arrastre');
  });

  // HU-16 RF-7
  it('con movimiento reducido no hay mano y explica los gestos', async () => {
    const fixture = await abrir(() => Promise.resolve(true), { movimientoReducido: true });
    const modelo = visor(fixture.nativeElement)!;
    expect(modelo.getAttribute('interaction-prompt')).toBe('none');

    modelo.dispatchEvent(new Event('load'));
    await fixture.whenStable();
    expect(texto(fixture.nativeElement)).toContain(
      'Arrastre para girar el modelo; pellizque o use la rueda para acercarlo. Con el teclado, use las flechas.',
    );
  });

  // Revisión de interfaz: el contorno de la tienda cuando el foco llega con el teclado, no con el mouse
  it('marca el foco que llega con el teclado', async () => {
    const fixture = await abrir(() => Promise.resolve(true));
    const modelo = visor(fixture.nativeElement)! as HTMLElement;
    const interno = document.createElement('div');
    interno.tabIndex = 0;
    modelo.attachShadow({ mode: 'open' }).append(interno);
    let conTeclado = true;
    vi.spyOn(interno, 'matches').mockImplementation((selector: string) => selector === ':focus-visible' && conTeclado);

    interno.focus();
    await fixture.whenStable();
    expect(modelo.classList).toContain('foco-teclado');

    // Con el foco puesto, arrastrar con el mouse lo apaga (/code-review, ronda 2)
    modelo.dispatchEvent(new Event('pointerdown'));
    await fixture.whenStable();
    expect(modelo.classList).not.toContain('foco-teclado');

    interno.blur();
    await fixture.whenStable();
    expect(modelo.classList).not.toContain('foco-teclado');

    conTeclado = false;
    interno.focus();
    await fixture.whenStable();
    expect(modelo.classList).not.toContain('foco-teclado');
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

  // HU-16 RF-10: los decodificadores salen de la tienda, no de gstatic.com
  it('configurarDecodificadores apunta a la tienda y no pisa lo que ya hay', () => {
    const vacio: Record<string, unknown> = {};
    configurarDecodificadores(vacio, '/3d/');
    expect(vacio['ModelViewerElement']).toEqual({
      dracoDecoderLocation: '/3d/draco/',
      ktx2TranscoderLocation: '/3d/basis/',
      meshoptDecoderLocation: '/3d/meshopt-incluido.js',
    });

    const conAlgo: Record<string, unknown> = { ModelViewerElement: { dracoDecoderLocation: '/otro/' } };
    configurarDecodificadores(conAlgo, '/decodificadores/');
    expect(conAlgo['ModelViewerElement']).toMatchObject({
      dracoDecoderLocation: '/otro/',
      ktx2TranscoderLocation: '/decodificadores/basis/',
    });
  });
});
