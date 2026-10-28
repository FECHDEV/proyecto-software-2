import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CARGAR_VISOR_3D, MOVIMIENTO_REDUCIDO } from '../visor-3d/visor-3d';
import { FotoDeGaleria, Galeria } from './galeria';

const FOTOS = ['/fotos/a.webp', '/fotos/b.webp', '/fotos/c.webp'];

// Como las arma el detalle (HU-22): la grande de 1200 con srcset, la miniatura de 400 y el original
function comoFoto(original: string): FotoDeGaleria {
  return {
    src: `${original}?ancho=1200`,
    srcset: `${original}?ancho=400 400w, ${original}?ancho=1200 1200w`,
    miniatura: `${original}?ancho=400`,
    original,
  };
}

// HU-10: las fotos del producto y su pantalla completa
describe('Galeria', () => {
  let fijo: ComponentFixture<Galeria>;

  async function abrir(fotos: string[] = FOTOS) {
    TestBed.resetTestingModule();
    fijo = TestBed.createComponent(Galeria);
    fijo.componentRef.setInput('fotos', fotos.map(comoFoto));
    fijo.componentRef.setInput('nombre', 'Taladro');
    fijo.autoDetectChanges();
    await fijo.whenStable();
  }

  function raiz(): HTMLElement {
    return fijo.nativeElement as HTMLElement;
  }

  function grande(): HTMLButtonElement {
    return raiz().querySelector('.foto-grande') as HTMLButtonElement;
  }

  function fotoGrande(): string | null | undefined {
    return grande().querySelector('img')?.getAttribute('src');
  }

  function miniaturas(): HTMLButtonElement[] {
    return Array.from(raiz().querySelectorAll('.miniaturas button'));
  }

  function dialogo(): HTMLDialogElement {
    return raiz().querySelector('dialog') as HTMLDialogElement;
  }

  function fotoAmpliada(): string | null | undefined {
    return dialogo().querySelector('.ampliada img')?.getAttribute('src');
  }

  function boton(clase: string): HTMLButtonElement {
    return dialogo().querySelector(`.${clase}`) as HTMLButtonElement;
  }

  async function tecla(key: string) {
    dialogo().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }));
    await fijo.whenStable();
  }

  async function ampliar() {
    grande().click();
    await fijo.whenStable();
  }

  // HU-22 RF-11: la grande elige con srcset, las miniaturas van con la de 400 y la pantalla completa con el original
  it('cada foto con la versión que le corresponde', async () => {
    await abrir();

    const foto = grande().querySelector('img')!;
    expect(foto.getAttribute('srcset')).toBe('/fotos/a.webp?ancho=400 400w, /fotos/a.webp?ancho=1200 1200w');
    expect(foto.getAttribute('sizes')).toBeTruthy();
    expect(miniaturas().map((cada) => cada.querySelector('img')?.getAttribute('src'))).toEqual([
      '/fotos/a.webp?ancho=400',
      '/fotos/b.webp?ancho=400',
      '/fotos/c.webp?ancho=400',
    ]);
    await ampliar();
    expect(fotoAmpliada()).toBe('/fotos/a.webp');
  });

  // HU-22 RF-11: el original pesa hasta 2 MB; con la pantalla completa cerrada no se pide
  it('el original se pide recién al abrir la pantalla completa', async () => {
    await abrir();
    expect(dialogo().querySelector('.ampliada img')).toBeNull();

    await ampliar();
    expect(fotoAmpliada()).toBe('/fotos/a.webp');
  });

  // HU-10 RF-5
  it('muestra la principal y las miniaturas', async () => {
    await abrir();

    expect(fotoGrande()).toBe('/fotos/a.webp?ancho=1200');
    expect(grande().querySelector('img')?.getAttribute('alt')).toBe('Taladro, foto 1 de 3');
    expect(miniaturas()).toHaveLength(3);
    expect(miniaturas()[0].getAttribute('aria-pressed')).toBe('true');
    expect(miniaturas()[1].getAttribute('aria-label')).toBe('Ver la foto 2 de 3');
  });

  // HU-10 RF-5
  it('elegir una miniatura la muestra grande', async () => {
    await abrir();

    miniaturas()[2].click();
    await fijo.whenStable();

    expect(fotoGrande()).toBe('/fotos/c.webp?ancho=1200');
    expect(miniaturas()[2].getAttribute('aria-pressed')).toBe('true');
    expect(miniaturas()[0].getAttribute('aria-pressed')).toBe('false');
  });

  // HU-10 RF-5: sin fotos o con una que no carga, el ícono, también en pantalla completa
  it('sin fotos o con una foto rota muestra el ícono', async () => {
    await abrir([]);
    expect(raiz().querySelector('img')).toBeNull();
    expect(raiz().querySelector('.icono-foto')).not.toBeNull();
    expect(raiz().querySelector('.foto-grande')).toBeNull();

    await abrir();
    grande().querySelector('img')?.dispatchEvent(new Event('error'));
    await fijo.whenStable();
    expect(grande().querySelector('img')).toBeNull();
    expect(grande().querySelector('.icono-foto')).not.toBeNull();

    await ampliar();
    expect(dialogo().querySelector('.ampliada img')).toBeNull();
    expect(dialogo().querySelector('.ampliada .icono-foto')).not.toBeNull();

    // La siguiente sí carga
    boton('siguiente').click();
    await fijo.whenStable();
    expect(fotoAmpliada()).toBe('/fotos/b.webp');
  });

  // HU-10 RF-5, RF-6
  it('con una sola foto no hay miniaturas ni flechas', async () => {
    await abrir(['/fotos/a.webp']);

    expect(miniaturas()).toHaveLength(0);
    await ampliar();
    expect(boton('anterior')).toBeNull();
    expect(boton('siguiente')).toBeNull();
  });

  // HU-10 RF-6
  it('abre a pantalla completa y pasa con flechas y teclado', async () => {
    await abrir();
    miniaturas()[1].click();
    await fijo.whenStable();

    await ampliar();
    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(fotoAmpliada()).toBe('/fotos/b.webp');
    // showModal() da el foco al primer botón, «Cerrar» (jsdom no lo aplica: se
    // verifica en el navegador)
    expect(dialogo().querySelector('button')).toBe(boton('cerrar'));

    boton('siguiente').click();
    await fijo.whenStable();
    expect(fotoAmpliada()).toBe('/fotos/c.webp');

    await tecla('ArrowLeft');
    await tecla('ArrowLeft');
    expect(fotoAmpliada()).toBe('/fotos/a.webp');
    await tecla('ArrowRight');
    expect(fotoAmpliada()).toBe('/fotos/b.webp');
    expect(dialogo().textContent).toContain('2 / 3');
    expect(dialogo().querySelector('.posicion .solo-lectores')?.textContent?.trim()).toBe('Foto 2 de 3');
  });

  // HU-10 RF-6: se detiene en la primera y en la última
  it('no da la vuelta en los extremos', async () => {
    await abrir();
    await ampliar();

    expect(boton('anterior').getAttribute('aria-disabled')).toBe('true');
    boton('anterior').click();
    await tecla('ArrowLeft');
    expect(fotoAmpliada()).toBe('/fotos/a.webp');

    await tecla('ArrowRight');
    await tecla('ArrowRight');
    await tecla('ArrowRight');
    expect(fotoAmpliada()).toBe('/fotos/c.webp');
    expect(boton('siguiente').getAttribute('aria-disabled')).toBe('true');
    expect(boton('anterior').getAttribute('aria-disabled')).toBe('false');
  });

  // HU-10 RF-6
  it('se cierra y devuelve el foco a la foto, que queda en la que se miraba', async () => {
    await abrir();
    await ampliar();
    await tecla('ArrowRight');

    boton('cerrar').click();
    await fijo.whenStable();

    expect(dialogo().hasAttribute('open')).toBe(false);
    expect(document.activeElement).toBe(grande());
    expect(fotoGrande()).toBe('/fotos/b.webp?ancho=1200');
  });

  // HU-10 RF-6
  it('deslizar con el dedo pasa de foto', async () => {
    await abrir();
    await ampliar();
    const ampliada = dialogo().querySelector('.ampliada') as HTMLElement;

    const dedo = (tipo: string, clientX: number, pointerId = 1, isPrimary = true) =>
      ampliada.dispatchEvent(Object.assign(new Event(tipo, { bubbles: true }), { clientX, pointerId, isPrimary }));
    const deslizar = async (desde: number, hasta: number) => {
      dedo('pointerdown', desde);
      dedo('pointerup', hasta);
      await fijo.whenStable();
    };

    await deslizar(300, 200);
    expect(fotoAmpliada()).toBe('/fotos/b.webp');
    await deslizar(200, 300);
    expect(fotoAmpliada()).toBe('/fotos/a.webp');
    // Un toque corto no es un deslizamiento
    await deslizar(200, 180);
    expect(fotoAmpliada()).toBe('/fotos/a.webp');

    // Un segundo dedo (un pellizco) no pasa de foto
    dedo('pointerdown', 300);
    dedo('pointerdown', 100, 2, false);
    dedo('pointerup', 50, 2, false);
    dedo('pointercancel', 300);
    dedo('pointerup', 100);
    await fijo.whenStable();
    expect(fotoAmpliada()).toBe('/fotos/a.webp');

    // El clic que cierra un deslizamiento no cierra la pantalla completa
    await deslizar(300, 200);
    ampliada.click();
    await fijo.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(true);

    // Revisión de HU-10: en el celular un deslizamiento no deja clic; el
    // toque siguiente afuera sí cierra
    await deslizar(300, 200);
    const controles = dialogo().querySelector('.controles') as HTMLElement;
    controles.dispatchEvent(Object.assign(new Event('pointerdown', { bubbles: true }), { pointerId: 2, isPrimary: true }));
    controles.click();
    await fijo.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(false);
  });

  // HU-10 RF-6: lo oscuro alrededor de la foto es el fondo; la foto y los botones no cierran
  it('tocar el fondo cierra', async () => {
    for (const fondo of ['.ampliada', '.controles', 'dialog']) {
      await abrir();
      await ampliar();

      (dialogo().querySelector('.ampliada img') as HTMLElement).click();
      boton('siguiente').click();
      await fijo.whenStable();
      expect(dialogo().hasAttribute('open')).toBe(true);

      (raiz().querySelector(fondo) as HTMLElement).click();
      await fijo.whenStable();
      expect(dialogo().hasAttribute('open')).toBe(false);
    }
  });

  // HU-10 RF-6: Escape lo cierra el navegador (evento close)
  it('con Escape se cierra y el foco vuelve a la foto', async () => {
    await abrir();
    await ampliar();

    dialogo().close();
    await fijo.whenStable();

    expect(document.activeElement).toBe(grande());
  });

  // HU-10 RF-5: al pasar a otro producto no queda la foto del anterior
  it('al cambiar las fotos vuelve a la primera', async () => {
    await abrir();
    miniaturas()[2].click();
    await fijo.whenStable();

    fijo.componentRef.setInput('fotos', ['/fotos/x.webp', '/fotos/y.webp'].map(comoFoto));
    await fijo.whenStable();

    expect(fotoGrande()).toBe('/fotos/x.webp?ancho=1200');
    expect(miniaturas()[0].getAttribute('aria-pressed')).toBe('true');
  });

  // --- HU-16: el modelo 3D en la galería ---

  const MODELO = '/api/productos/7/modelo/m.glb';
  let cargar: ReturnType<typeof vi.fn<() => Promise<boolean>>>;

  async function abrirConModelo(fotos: string[] = FOTOS, disponible = true) {
    TestBed.resetTestingModule();
    cargar = vi.fn(() => Promise.resolve(disponible));
    TestBed.configureTestingModule({
      providers: [
        { provide: CARGAR_VISOR_3D, useValue: cargar },
        { provide: MOVIMIENTO_REDUCIDO, useValue: () => false },
      ],
    });
    fijo = TestBed.createComponent(Galeria);
    fijo.componentRef.setInput('fotos', fotos.map(comoFoto));
    fijo.componentRef.setInput('nombre', 'Taladro');
    fijo.componentRef.setInput('modelo', MODELO);
    fijo.autoDetectChanges();
    await fijo.whenStable();
  }

  function botonDeVista(): HTMLButtonElement | null {
    return raiz().querySelector('.vista');
  }

  function visor(): Element | null {
    return raiz().querySelector('app-visor-3d');
  }

  async function cambiarVista() {
    botonDeVista()!.click();
    await fijo.whenStable();
  }

  // HU-16 RF-3
  it('sin modelo no ofrece Ver en 3D', async () => {
    await abrir();

    expect(botonDeVista()).toBeNull();
    expect(raiz().textContent).not.toContain('Ver en 3D');
  });

  // HU-16 RF-3, RF-4
  it('con modelo, Ver en 3D cambia la foto por el visor con el póster', async () => {
    await abrirConModelo();
    expect(botonDeVista()!.textContent).toContain('Ver en 3D');

    await cambiarVista();

    expect(grande()).toBeNull();
    const modelo = raiz().querySelector('model-viewer')!;
    expect(modelo.getAttribute('src')).toBe(MODELO);
    // HU-22 RF-11: el póster es la de 1200
    expect(modelo.getAttribute('poster')).toBe(`${FOTOS[0]}?ancho=1200`);
    expect(modelo.getAttribute('alt')).toBe('Modelo 3D de Taladro');
    // En la tienda, con realidad aumentada
    expect(modelo.hasAttribute('ar')).toBe(true);
    expect(botonDeVista()!.textContent).toContain('Ver fotos');
  });

  // HU-16 RF-4: el visor y el modelo se descargan recién al tocar
  it('el visor no se crea antes de tocar Ver en 3D', async () => {
    await abrirConModelo();

    expect(visor()).toBeNull();
    expect(cargar).not.toHaveBeenCalled();
  });

  // HU-16 RF-6
  it('Ver fotos vuelve a la foto que estaba', async () => {
    await abrirConModelo();
    miniaturas()[1].click();
    await fijo.whenStable();

    await cambiarVista();
    // En 3D ninguna miniatura está elegida
    expect(miniaturas().map((cada) => cada.getAttribute('aria-pressed'))).toEqual(['false', 'false', 'false']);
    await cambiarVista();

    expect(visor()).toBeNull();
    expect(fotoGrande()).toBe(`${FOTOS[1]}?ancho=1200`);
  });

  // HU-16 RF-6
  it('una miniatura vuelve a esa foto', async () => {
    await abrirConModelo();
    await cambiarVista();

    miniaturas()[2].click();
    await fijo.whenStable();

    expect(visor()).toBeNull();
    expect(fotoGrande()).toBe(`${FOTOS[2]}?ancho=1200`);
    expect(botonDeVista()!.textContent).toContain('Ver en 3D');
  });

  // HU-16 caso límite: otro producto desde «Más de…»
  it('con otro producto vuelve a las fotos', async () => {
    await abrirConModelo();
    await cambiarVista();

    fijo.componentRef.setInput('fotos', ['/fotos/x.webp'].map(comoFoto));
    fijo.componentRef.setInput('modelo', '/api/productos/8/modelo/n.glb');
    await fijo.whenStable();

    expect(visor()).toBeNull();
    expect(fotoGrande()).toBe('/fotos/x.webp?ancho=1200');
  });

  // /code-review: una foto principal rota no es el póster
  it('sin póster si la foto principal está rota', async () => {
    await abrirConModelo();
    grande().querySelector('img')!.dispatchEvent(new Event('error'));
    await fijo.whenStable();

    await cambiarVista();

    expect(raiz().querySelector('model-viewer')!.hasAttribute('poster')).toBe(false);
  });

  // /code-review: las mismas fotos en otra lista (el producto se volvió a leer) no sacan del modelo, y «Ver fotos»
  // vuelve a la que estaba (ronda 2)
  it('las mismas fotos otra vez no sacan del modelo', async () => {
    await abrirConModelo();
    miniaturas()[2].click();
    await fijo.whenStable();
    await cambiarVista();

    fijo.componentRef.setInput('fotos', FOTOS.map(comoFoto));
    await fijo.whenStable();
    expect(visor()).not.toBeNull();

    await cambiarVista();
    expect(fotoGrande()).toBe(`${FOTOS[2]}?ancho=1200`);
  });

  // HU-16 caso límite: un modelo sin fotos
  it('con modelo y sin fotos ofrece Ver en 3D sin póster', async () => {
    await abrirConModelo([]);
    expect(raiz().querySelector('.foto-sola')).not.toBeNull();

    await cambiarVista();

    const modelo = raiz().querySelector('model-viewer')!;
    expect(modelo.getAttribute('src')).toBe(MODELO);
    expect(modelo.hasAttribute('poster')).toBe(false);
    expect(raiz().querySelector('.foto-sola')).toBeNull();
  });

  // HU-16 caso límite: la pantalla completa es solo de las fotos
  it('en 3D no hay pantalla completa', async () => {
    await abrirConModelo();
    await cambiarVista();

    expect(grande()).toBeNull();
    expect(raiz().querySelector('[aria-haspopup="dialog"]')).toBeNull();
  });

  // HU-16 RF-9
  it('si el visor falla, Ver fotos sigue', async () => {
    await abrirConModelo(FOTOS, false);
    await cambiarVista();
    expect(raiz().textContent).toContain('No pudimos mostrar el modelo en este dispositivo.');

    await cambiarVista();

    expect(fotoGrande()).toBe(`${FOTOS[0]}?ancho=1200`);
  });

  // HU-16 caso límite «Teclado»: el botón no se reemplaza, así el foco no se pierde
  it('al cambiar de vista el foco queda en el botón', async () => {
    await abrirConModelo();
    const boton = botonDeVista()!;
    boton.focus();

    await cambiarVista();
    expect(document.activeElement).toBe(boton);
    expect(boton.textContent).toContain('Ver fotos');

    await cambiarVista();
    expect(document.activeElement).toBe(boton);
  });
});
