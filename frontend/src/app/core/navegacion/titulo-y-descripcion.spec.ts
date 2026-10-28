import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { provideRouter, Router, TitleStrategy } from '@angular/router';

import { MARCA } from '../marca';
import { resumen, TituloYDescripcion } from './titulo-y-descripcion';

@Component({ template: '' })
class PantallaVacia {}

// Visibilidad (plan 2026-10-28): el título y la descripción para los buscadores
describe('TituloYDescripcion', () => {
  let router: Router;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: '', title: MARCA.nombre, component: PantallaVacia },
          {
            path: 'catalogo',
            title: 'Catálogo',
            data: { descripcion: 'Todo el catálogo.' },
            component: PantallaVacia,
          },
          { path: 'seccion', data: { descripcion: 'De la sección.' }, children: [{ path: 'hija', component: PantallaVacia }] },
        ]),
        { provide: TitleStrategy, useClass: TituloYDescripcion },
      ],
    });
    router = TestBed.inject(Router);
  });

  function descripcion(): string | null | undefined {
    return TestBed.inject(Meta).getTag('name="description"')?.content;
  }

  it('la ruta pone su título y su descripción', async () => {
    await router.navigateByUrl('/catalogo');

    expect(TestBed.inject(Title).getTitle()).toBe('Catálogo');
    expect(descripcion()).toBe('Todo el catálogo.');
  });

  it('sin descripción queda la general', async () => {
    await router.navigateByUrl('/catalogo');
    await router.navigateByUrl('/');

    expect(descripcion()).toBe(MARCA.descripcion);
  });

  it('una ruta hija usa la descripción de su sección', async () => {
    await router.navigateByUrl('/seccion/hija');

    expect(descripcion()).toBe('De la sección.');
  });
});

describe('resumen', () => {
  it('un texto corto queda igual, en una sola línea', () => {
    expect(resumen('Para concreto y madera.\nCon maletín.')).toBe('Para concreto y madera. Con maletín.');
  });

  it('uno largo se recorta sin cortar palabras', () => {
    const largo = 'palabra '.repeat(40).trim();

    const corto = resumen(largo, 30);

    expect(corto).toBe('palabra palabra palabra…');
    expect(corto.length).toBeLessThanOrEqual(30);
  });

  it('no deja puntuación antes de los puntos suspensivos', () => {
    expect(resumen('Para concreto, madera y metal.', 20)).toBe('Para concreto…');
  });

  it('un texto con solo espacios queda vacío', () => {
    expect(resumen('  \n ')).toBe('');
  });

  it('una palabra sin espacios más larga que el máximo se corta igual', () => {
    expect(resumen('a'.repeat(50), 10)).toBe(`${'a'.repeat(9)}…`);
  });
});
