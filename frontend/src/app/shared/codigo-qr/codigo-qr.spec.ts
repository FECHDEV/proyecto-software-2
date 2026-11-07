import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { CodigoQr, descargarQr, ESPERA_PARA_LIBERAR } from './codigo-qr';

// 22 caracteres aptos para URL
const CODIGO = 'x3Vb9QkLm2PzR7tYcW4sNa';

describe('CodigoQr (HU-13 RF-1)', () => {
  async function dibujar(valor: string, etiqueta = 'Código QR de prueba'): Promise<ComponentFixture<CodigoQr>> {
    const fijo = TestBed.createComponent(CodigoQr);
    fijo.componentRef.setInput('valor', valor);
    fijo.componentRef.setInput('etiqueta', etiqueta);
    fijo.autoDetectChanges();
    await fijo.whenStable();
    return fijo;
  }

  function svg(fijo: ComponentFixture<CodigoQr>): SVGSVGElement | null {
    return fijo.nativeElement.querySelector('svg');
  }

  function trazo(fijo: ComponentFixture<CodigoQr>): string {
    return svg(fijo)?.querySelector('path')?.getAttribute('d') ?? '';
  }

  it('es una imagen con el nombre accesible recibido', async () => {
    const fijo = await dibujar(CODIGO, 'Código QR para pagar el pedido N.º 12');

    expect(svg(fijo)?.getAttribute('role')).toBe('img');
    expect(svg(fijo)?.getAttribute('aria-label')).toBe('Código QR para pagar el pedido N.º 12');
  });

  // 22 bytes con corrección M piden la versión 2: 25 módulos, más 4 de zona silenciosa por lado
  it('deja la zona silenciosa de 4 módulos alrededor', async () => {
    const fijo = await dibujar(CODIGO);

    expect(svg(fijo)?.getAttribute('viewBox')).toBe('0 0 33 33');
  });

  // La esquina del patrón de posición de arriba a la izquierda siempre es oscura
  it('dibuja los módulos oscuros desplazados por la zona silenciosa', async () => {
    const fijo = await dibujar(CODIGO);

    expect(trazo(fijo).startsWith('M4 4h1v1h-1z')).toBe(true);
  });

  it('el mismo valor da el mismo dibujo, y otro valor otro distinto', async () => {
    const uno = trazo(await dibujar(CODIGO));
    const igual = trazo(await dibujar(CODIGO));
    const otro = trazo(await dibujar('otro-codigo-de-cobro'));

    expect(igual).toBe(uno);
    expect(otro).not.toBe(uno);
  });

  it('al cambiar el valor se redibuja', async () => {
    const fijo = await dibujar(CODIGO);
    const antes = trazo(fijo);

    fijo.componentRef.setInput('valor', 'otro-codigo-de-cobro');
    await fijo.whenStable();

    expect(trazo(fijo)).not.toBe(antes);
  });

  // Un cobro real trae un texto largo
  it('un texto largo se dibuja en una versión más grande', async () => {
    const fijo = await dibujar('0'.repeat(300));

    const lado = Number(svg(fijo)?.getAttribute('viewBox')?.split(' ')[2]);
    expect(lado).toBeGreaterThan(33);
    expect(trazo(fijo)).not.toBe('');
  });

  it('sin valor no dibuja nada', async () => {
    const fijo = await dibujar('');

    expect(svg(fijo)).toBeNull();
  });
});

// jsdom no dibuja en un canvas: el contexto y toBlob se reemplazan y se mira qué se pidió
describe('descargarQr', () => {
  let rectangulos: { color: string; x: number; y: number; lado: number }[];
  // El tamaño del canvas cuando se pidió su contexto, ya fijado
  let lienzo: { ancho: number; alto: number } | undefined;
  let descargado: { nombre: string; href: string } | undefined;

  beforeEach(() => {
    rectangulos = [];
    lienzo = undefined;
    descargado = undefined;
    const contexto = {
      fillStyle: '',
      fillRect(x: number, y: number, ancho: number, alto: number) {
        expect(alto).toBe(ancho);
        rectangulos.push({ color: this.fillStyle, x, y, lado: ancho });
      },
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(function (this: HTMLCanvasElement) {
      lienzo = { ancho: this.width, alto: this.height };
      return contexto as unknown as CanvasRenderingContext2D;
    } as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((listo) =>
      listo(new Blob(['png'], { type: 'image/png' })),
    );
    URL.createObjectURL = vi.fn(() => 'blob:qr');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      descargado = { nombre: this.download, href: this.href };
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('baja un PNG con el nombre pedido, la zona silenciosa y fondo claro', async () => {
    vi.useFakeTimers();
    await descargarQr(CODIGO, 'pedido-12-qr.png');

    expect(descargado).toEqual({ nombre: 'pedido-12-qr.png', href: 'blob:qr' });
    // 33 módulos (25 + 4 de zona silenciosa por lado), de 12 px cada uno
    expect(lienzo).toEqual({ ancho: 33 * 12, alto: 33 * 12 });
    expect(rectangulos[0]).toEqual({ color: '#ffffff', x: 0, y: 0, lado: 33 * 12 });
    // La esquina del patrón de posición, desplazada por la zona silenciosa
    expect(rectangulos[1]).toEqual({ color: '#000000', x: 4 * 12, y: 4 * 12, lado: 12 });
    // La dirección del archivo se libera después, no en el acto
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    vi.advanceTimersByTime(ESPERA_PARA_LIBERAR);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:qr');
  });

  it('los módulos oscuros son los mismos que dibuja el componente', async () => {
    const fijo = TestBed.createComponent(CodigoQr);
    fijo.componentRef.setInput('valor', CODIGO);
    fijo.componentRef.setInput('etiqueta', 'Código QR de prueba');
    fijo.detectChanges();
    const enPantalla = (fijo.nativeElement.querySelector('path')?.getAttribute('d') ?? '').split('z').length - 1;

    await descargarQr(CODIGO, 'qr.png');

    expect(rectangulos.length - 1).toBe(enPantalla);
  });

  it('sin valor no baja nada', async () => {
    await descargarQr('', 'qr.png');

    expect(descargado).toBeUndefined();
  });
});
