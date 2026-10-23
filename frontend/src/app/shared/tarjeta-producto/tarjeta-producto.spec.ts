import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { DatosDeTarjeta, TarjetaProducto } from './tarjeta-producto';

const MARTILLO: DatosDeTarjeta = {
  nombre: 'Martillo de carpintero 16 oz',
  marca: 'Truper',
  precio: 85,
  unidad: null,
  imagen: '/api/productos/7/imagenes/a.jpg',
  agotado: false,
  enlace: '/producto/7',
  conModelo: false,
};

@Component({
  imports: [TarjetaProducto],
  template: `<app-tarjeta-producto [datos]="datos()" />`,
})
class Anfitrion {
  readonly datos = signal<DatosDeTarjeta>(MARTILLO);
}

// HU-09: la tarjeta de un producto en la tienda
describe('TarjetaProducto', () => {
  let fijo: ComponentFixture<Anfitrion>;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fijo = TestBed.createComponent(Anfitrion);
    fijo.autoDetectChanges();
    await fijo.whenStable();
  });

  async function con(cambios: Partial<DatosDeTarjeta>): Promise<void> {
    fijo.componentInstance.datos.set({ ...MARTILLO, ...cambios });
    await fijo.whenStable();
  }

  function texto(): string {
    return (fijo.nativeElement.textContent ?? '').replace(/\s+/g, ' ');
  }

  // HU-16 RF-11
  it('marca 3D con texto para lectores si tiene modelo', async () => {
    await con({ conModelo: true });

    const marca = fijo.nativeElement.querySelector('.tres-d');
    expect(marca.textContent.trim()).toBe('3D');
    // El enlace se anuncia con «Con vista 3D», no con un «3D» suelto, y después del nombre (revisión de interfaz)
    expect(marca.getAttribute('aria-hidden')).toBe('true');
    const nombre = fijo.nativeElement.querySelector('.nombre');
    const paraLectores = fijo.nativeElement.querySelector('.solo-lectores');
    expect(paraLectores.textContent.trim()).toBe('Con vista 3D');
    expect(nombre.compareDocumentPosition(paraLectores) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  // /code-review: sin foto (o con la foto rota) la marca va invertida, y no queda dentro de la foto que se gasta
  it('la marca va fuera de la foto e invertida sin foto', async () => {
    await con({ conModelo: true, agotado: true });
    const marca = () => fijo.nativeElement.querySelector('.tres-d') as HTMLElement;
    expect(marca().closest('.foto')).toBeNull();
    expect(marca().classList).not.toContain('sobre-oscuro');

    await con({ conModelo: true, imagen: null });
    expect(marca().classList).toContain('sobre-oscuro');
  });

  // HU-16 RF-11
  it('sin modelo no hay marca', () => {
    expect(fijo.nativeElement.querySelector('.tres-d')).toBeNull();
    expect(texto()).not.toContain('3D');
  });

  // HU-09 RF-6
  it('muestra nombre, marca y precio con su unidad', async () => {
    await con({ unidad: 'metro', precio: 12.5 });

    expect(texto()).toContain('Martillo de carpintero 16 oz');
    expect(texto()).toContain('Truper');
    expect(texto()).toMatch(/Bs\s12,50 \/ metro/);
  });

  // HU-09 RF-6: la línea de la marca se reserva vacía, así los nombres de una fila quedan alineados
  it('sin marca deja la línea vacía', async () => {
    await con({ marca: null });

    expect(fijo.nativeElement.querySelector('.marca').textContent.trim()).toBe('');
  });

  // Una foto que no carga muestra el ícono
  it('una foto rota muestra el ícono', async () => {
    fijo.nativeElement.querySelector('img').dispatchEvent(new Event('error'));
    await fijo.whenStable();

    expect(fijo.nativeElement.querySelector('img')).toBeNull();
    expect(fijo.nativeElement.querySelector('.sin-foto svg')).not.toBeNull();
  });

  // HU-09 RF-6
  it('sin foto muestra un ícono', async () => {
    await con({ imagen: null });

    expect(fijo.nativeElement.querySelector('img')).toBeNull();
    expect(fijo.nativeElement.querySelector('.sin-foto svg')).not.toBeNull();
  });

  // HU-09 RF-6: la foto no repite el nombre para el lector de pantalla
  it('la foto es decorativa', () => {
    const foto: HTMLImageElement = fijo.nativeElement.querySelector('img');

    expect(foto.getAttribute('src')).toBe('/api/productos/7/imagenes/a.jpg');
    expect(foto.alt).toBe('');
    expect(foto.getAttribute('loading')).toBe('lazy');
  });

  // HU-09 RF-7
  it('agotado con ícono y texto', async () => {
    expect(texto()).not.toContain('Agotado');

    await con({ agotado: true });

    const marca: HTMLElement = fijo.nativeElement.querySelector('.agotado');
    expect(marca.textContent?.trim()).toBe('Agotado');
    expect(marca.querySelector('svg')).not.toBeNull();
  });

  // HU-09 RF-6
  it('toda la tarjeta es un enlace', () => {
    const enlaces: HTMLAnchorElement[] = Array.from(fijo.nativeElement.querySelectorAll('a'));

    expect(enlaces).toHaveLength(1);
    expect(enlaces[0].getAttribute('href')).toBe('/producto/7');
    expect(enlaces[0].textContent).toContain('Martillo de carpintero 16 oz');
  });
});
