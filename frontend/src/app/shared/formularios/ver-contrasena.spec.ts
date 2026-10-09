import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { VerContrasena } from './ver-contrasena';

// Dos campos en la misma plantilla, como «Mis datos» (actual y nueva)
@Component({
  imports: [VerContrasena],
  template: `
    <form>
      <input id="actual" #actual type="password" value="vieja123" />
      <button appVerContrasena [campo]="actual"></button>
      <input id="nueva" #nueva type="password" value="nueva1234" />
      <button appVerContrasena [campo]="nueva"></button>
    </form>
  `,
})
class DosCampos {}

// HU-04 RF-12: el botón para mostrar u ocultar la contraseña, en shared/
describe('VerContrasena', () => {
  let fijo: ComponentFixture<DosCampos>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [DosCampos] }).compileComponents();
    fijo = TestBed.createComponent(DosCampos);
    fijo.detectChanges();
  });

  function campo(id: string): HTMLInputElement {
    return fijo.nativeElement.querySelector('#' + id);
  }

  function boton(indice: number): HTMLButtonElement {
    return fijo.nativeElement.querySelectorAll('button[appVerContrasena]')[indice];
  }

  // HU-04 RF-12
  it('empieza oculta y ofrece mostrarla', () => {
    expect(campo('actual').type).toBe('password');
    expect(boton(0).getAttribute('aria-label')).toBe('Mostrar contraseña');
  });

  // HU-04 RF-12
  it('muestra y oculta solo su campo', () => {
    boton(0).click();
    fijo.detectChanges();

    expect(campo('actual').type).toBe('text');
    expect(campo('actual').value).toBe('vieja123');
    expect(campo('nueva').type).toBe('password');
    expect(boton(0).getAttribute('aria-label')).toBe('Ocultar contraseña');
    expect(boton(1).getAttribute('aria-label')).toBe('Mostrar contraseña');

    boton(0).click();
    fijo.detectChanges();

    expect(campo('actual').type).toBe('password');
  });

  // HU-04 RF-12: con dos campos, cada botón dice cuál controla
  it('indica qué campo controla', () => {
    expect(boton(0).getAttribute('aria-controls')).toBe('actual');
    expect(boton(1).getAttribute('aria-controls')).toBe('nueva');
    expect(boton(0).getAttribute('title')).toBe('Mostrar contraseña');
  });

  // HU-04 RF-12: está dentro del formulario, pero no lo envía
  it('no envía el formulario', () => {
    let enviado = false;
    fijo.nativeElement.querySelector('form').addEventListener('submit', (evento: Event) => {
      evento.preventDefault();
      enviado = true;
    });

    boton(0).click();

    expect(boton(0).type).toBe('button');
    expect(enviado).toBe(false);
  });
});
