import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ArchivoOcupado } from './archivo-ocupado';

@Component({
  imports: [ArchivoOcupado],
  template: `<input type="file" [ocupado]="ocupado()" />`,
})
class Anfitrion {
  readonly ocupado = signal(false);
}

describe('ArchivoOcupado', () => {
  let fijo: ComponentFixture<Anfitrion>;
  const campo = () => fijo.nativeElement.querySelector('input') as HTMLInputElement;
  const pulsar = () => {
    const clic = new MouseEvent('click', { cancelable: true });
    campo().dispatchEvent(clic);
    return clic;
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    fijo = TestBed.createComponent(Anfitrion);
    fijo.autoDetectChanges();
    await fijo.whenStable();
  });

  it('libre, abre el selector de archivos', () => {
    expect(campo().hasAttribute('aria-disabled')).toBe(false);
    expect(pulsar().defaultPrevented).toBe(false);
  });

  // aria-disabled y no disabled: el foco se queda en el campo si la subida falla
  it('ocupado, no se deshabilita ni pierde el foco, pero no abre el selector', async () => {
    campo().focus();
    fijo.componentInstance.ocupado.set(true);
    await fijo.whenStable();

    expect(campo().getAttribute('aria-disabled')).toBe('true');
    expect(campo().disabled).toBe(false);
    expect(document.activeElement).toBe(campo());
    expect(pulsar().defaultPrevented).toBe(true);
  });
});
