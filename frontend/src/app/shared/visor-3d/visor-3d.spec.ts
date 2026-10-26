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

  // HU-16 RF-12 a RF-14: jsdom no tiene la pantalla completa; se simula la del navegador
  describe('pantalla completa', () => {
    let ampliado: Element | null = null;
    let rechazar = false;

    function simular(permitida: boolean) {
      Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, value: permitida });
      Object.defineProperty(document, 'fullscreenElement', { configurable: true, get: () => ampliado });
      HTMLElement.prototype.requestFullscreen = vi.fn(function (this: HTMLElement) {
        if (rechazar) {
          return Promise.reject(new TypeError('no permitida'));
        }
        ampliado = this;
        document.dispatchEvent(new Event('fullscreenchange'));
        return Promise.resolve();
      });
      document.exitFullscreen = vi.fn(() => {
        ampliado = null;
        document.dispatchEvent(new Event('fullscreenchange'));
        return Promise.resolve();
      });
    }

    afterEach(() => {
      ampliado = null;
      rechazar = false;
      delete (document as unknown as Record<string, unknown>)['fullscreenEnabled'];
      delete (document as unknown as Record<string, unknown>)['fullscreenElement'];
      delete (document as unknown as Record<string, unknown>)['exitFullscreen'];
      delete (HTMLElement.prototype as unknown as Record<string, unknown>)['requestFullscreen'];
    });

    async function cargarModelo(fixture: Awaited<ReturnType<typeof abrir>>) {
      visor(fixture.nativeElement)!.dispatchEvent(new Event('load'));
      await fixture.whenStable();
    }

    const boton = (elemento: HTMLElement) => elemento.querySelector<HTMLButtonElement>('button.ampliar');

    // HU-16 RF-12
    it('ofrece «Pantalla completa» si el navegador la permite', async () => {
      simular(true);
      const fixture = await abrir(() => Promise.resolve(true));
      await cargarModelo(fixture);

      expect(boton(fixture.nativeElement)?.getAttribute('aria-label')).toBe('Pantalla completa');
      expect(boton(fixture.nativeElement)?.getAttribute('title')).toBe('Pantalla completa');
    });

    // HU-16 RF-12: Safari del iPhone no la da a los elementos
    it('sin la pantalla completa del navegador (iPhone) no hay botón', async () => {
      simular(false);
      const fixture = await abrir(() => Promise.resolve(true));
      await cargarModelo(fixture);

      expect(visor(fixture.nativeElement)).not.toBeNull();
      expect(boton(fixture.nativeElement)).toBeNull();
    });

    // HU-16 RF-12, caso límite: sin modelo que mostrar (RF-9) ni mientras se descarga el visor
    it('sin modelo que mostrar no hay botón', async () => {
      simular(true);
      let listo!: (disponible: boolean) => void;
      const fixture = await abrir(() => new Promise((resolver) => (listo = resolver)));
      expect(boton(fixture.nativeElement)).toBeNull();

      listo(true);
      await fixture.whenStable();
      // Mientras el modelo carga, tampoco (RF-12: «mientras se ve el modelo»; /code-review)
      expect(boton(fixture.nativeElement)).toBeNull();
      visor(fixture.nativeElement)!.dispatchEvent(new Event('error'));
      await fixture.whenStable();
      expect(boton(fixture.nativeElement)).toBeNull();
    });

    // HU-16 RF-13: se amplía el marco, con el modelo y el botón adentro
    it('«Pantalla completa» amplía el marco del visor', async () => {
      simular(true);
      const fixture = await abrir(() => Promise.resolve(true));
      const elemento = fixture.nativeElement as HTMLElement;
      await cargarModelo(fixture);
      expect(visor(elemento)!.getAttribute('touch-action')).toBe('pan-y');

      boton(elemento)!.click();
      await fixture.whenStable();

      expect(ampliado).toBe(elemento.querySelector('.marco'));
      expect(ampliado!.contains(visor(elemento))).toBe(true);
      // Sin página que desplazar, el arrastre vertical también inclina el modelo (/code-review)
      expect(visor(elemento)!.getAttribute('touch-action')).toBe('none');
      expect(boton(elemento)!.getAttribute('aria-label')).toBe('Salir de pantalla completa');
    });

    // HU-16 RF-7, RF-13: con movimiento reducido, el texto de los gestos se sigue viendo en pantalla completa
    it('en pantalla completa el texto de los gestos va dentro del marco', async () => {
      simular(true);
      const fixture = await abrir(() => Promise.resolve(true), { movimientoReducido: true });
      const elemento = fixture.nativeElement as HTMLElement;
      visor(elemento)!.dispatchEvent(new Event('load'));
      await fixture.whenStable();
      expect(elemento.querySelectorAll('.gestos')).toHaveLength(1);
      expect(elemento.querySelector('.marco .gestos')).toBeNull();

      boton(elemento)!.click();
      await fixture.whenStable();

      expect(elemento.querySelectorAll('.gestos')).toHaveLength(1);
      expect(elemento.querySelector('.marco .gestos')?.textContent).toContain('Arrastre para girar el modelo');
    });

    // HU-16 RF-14: si el modelo cambia mientras está ampliado, el botón para salir sigue (/code-review, ronda 2)
    it('con otro modelo en pantalla completa sigue el botón para salir', async () => {
      simular(true);
      const fixture = await abrir(() => Promise.resolve(true));
      const elemento = fixture.nativeElement as HTMLElement;
      await cargarModelo(fixture);
      boton(elemento)!.click();
      await fixture.whenStable();

      fixture.componentInstance.src.set('/api/productos/7/modelo/b.glb');
      await fixture.whenStable();

      expect(boton(elemento)?.getAttribute('aria-label')).toBe('Salir de pantalla completa');
    });

    // HU-16 RF-13, caso límite: el navegador la rechaza
    it('si el navegador la rechaza, el visor sigue igual', async () => {
      simular(true);
      rechazar = true;
      const fixture = await abrir(() => Promise.resolve(true));
      const elemento = fixture.nativeElement as HTMLElement;
      await cargarModelo(fixture);

      boton(elemento)!.click();
      await fixture.whenStable();

      expect(ampliado).toBeNull();
      expect(visor(elemento)).not.toBeNull();
      expect(boton(elemento)!.getAttribute('aria-label')).toBe('Pantalla completa');
    });

    // HU-16 RF-14
    it('«Salir de pantalla completa» la cierra', async () => {
      simular(true);
      const fixture = await abrir(() => Promise.resolve(true));
      const elemento = fixture.nativeElement as HTMLElement;
      await cargarModelo(fixture);
      boton(elemento)!.click();
      await fixture.whenStable();

      boton(elemento)!.click();
      await fixture.whenStable();

      expect(document.exitFullscreen).toHaveBeenCalledTimes(1);
      expect(ampliado).toBeNull();
      expect(boton(elemento)!.getAttribute('aria-label')).toBe('Pantalla completa');
      // En su lugar, el arrastre vertical vuelve a desplazar la página
      expect(visor(elemento)!.getAttribute('touch-action')).toBe('pan-y');
    });

    // HU-16 RF-14, caso límite: Esc, el gesto atrás o F11 los resuelve el navegador
    it('al salir con Esc o el gesto atrás, el botón vuelve a «Pantalla completa»', async () => {
      simular(true);
      const fixture = await abrir(() => Promise.resolve(true));
      const elemento = fixture.nativeElement as HTMLElement;
      await cargarModelo(fixture);
      boton(elemento)!.click();
      await fixture.whenStable();

      ampliado = null;
      document.dispatchEvent(new Event('fullscreenchange'));
      await fixture.whenStable();

      expect(boton(elemento)!.getAttribute('aria-label')).toBe('Pantalla completa');
      expect(document.exitFullscreen).not.toHaveBeenCalled();
    });
  });
});
