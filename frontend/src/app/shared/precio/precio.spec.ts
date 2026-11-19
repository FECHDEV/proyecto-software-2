import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Precio } from './precio';

@Component({
  imports: [Precio],
  template: `<app-precio [precio]="precio()" [precioNormal]="normal()" [descuento]="descuento()" />`,
})
class Anfitrion {
  readonly precio = signal(85);
  readonly normal = signal<number | null>(null);
  readonly descuento = signal<number | null>(null);
}

// HU-24 RF-11: el precio con descuento en pantalla
describe('Precio', () => {
  let fijo: ComponentFixture<Anfitrion>;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    fijo = TestBed.createComponent(Anfitrion);
    fijo.autoDetectChanges();
    await fijo.whenStable();
  });

  async function conDescuento(): Promise<void> {
    fijo.componentInstance.normal.set(100);
    fijo.componentInstance.descuento.set(15);
    await fijo.whenStable();
  }

  function el(selector: string): HTMLElement | null {
    return fijo.nativeElement.querySelector(selector);
  }

  function limpio(texto: string | null | undefined): string {
    return (texto ?? '').replace(/\s+/g, ' ').trim();
  }

  it('sin descuento muestra solo el precio', () => {
    expect(limpio(fijo.nativeElement.textContent)).toBe('Bs 85,00');
    expect(el('s')).toBeNull();
    expect(el('.marca')).toBeNull();
  });

  it('RF-11: con descuento tacha el normal, muestra el nuevo y «−15 %»', async () => {
    await conDescuento();

    expect(limpio(el('s')?.textContent)).toBe('Bs 100,00');
    expect(limpio(el('.cobrado')?.textContent)).toBe('Bs 85,00');
    expect(limpio(el('.marca')?.textContent)).toBe('−15 %');
  });

  it('RF-11: lo dice entero a los lectores de pantalla', async () => {
    await conDescuento();

    expect(limpio(el('.solo-lectores')?.textContent)).toBe(
      'Precio normal Bs 100,00, ahora Bs 85,00, 15 % de descuento',
    );
    // Lo visible no se lee dos veces
    expect(el('.con-descuento')?.getAttribute('aria-hidden')).toBe('true');
  });

  it('un descuento sin precio normal se muestra como sin descuento', async () => {
    fijo.componentInstance.descuento.set(15);
    await fijo.whenStable();

    expect(limpio(fijo.nativeElement.textContent)).toBe('Bs 85,00');
  });
});
