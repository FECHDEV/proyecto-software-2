import { Component, DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Meta, Title } from '@angular/platform-browser';
import { provideRouter, Router, TitleStrategy } from '@angular/router';

import { MARCA } from '../marca';
import { ponerDatosEstructurados, resumen, TituloYDescripcion } from './titulo-y-descripcion';

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
          { path: '**', data: { indexar: false }, component: PantallaVacia },
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

  // SEO (plan 2026-11-07): la dirección canónica, sin la consulta, para que los filtros no cuenten como otras páginas
  it('pone la dirección canónica sin la consulta', async () => {
    await router.navigateByUrl('/catalogo?pagina=2&categoria=3');

    expect(canonica()).toBe(`${location.origin}/catalogo`);
    await router.navigateByUrl('/');
    expect(canonica()).toBe(`${location.origin}/`);
    expect(document.head.querySelectorAll('link[rel="canonical"]')).toHaveLength(1);
  });

  // SEO: «Página no encontrada» responde 200 (la arma el navegador): que no se indexe; la próxima página sí
  it('noIndexaLaPaginaNoEncontrada', async () => {
    await router.navigateByUrl('/no-existe');
    expect(robots()).toBe('noindex');

    await router.navigateByUrl('/catalogo');
    expect(robots()).toBeUndefined();
  });

  // SEO: los datos del producto los pone el detalle; al irse, no quedan en otra página
  it('alNavegarSacaLosDatosEstructurados', async () => {
    ponerDatosEstructurados(TestBed.inject(DOCUMENT), { '@type': 'Product', name: 'Martillo' });
    expect(document.head.querySelector('script[type="application/ld+json"]')?.textContent).toContain('Martillo');

    await router.navigateByUrl('/catalogo');

    expect(document.head.querySelector('script[type="application/ld+json"]')).toBeNull();
  });
});

function canonica(): string | null | undefined {
  return document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')?.href;
}

function robots(): string | undefined {
  return TestBed.inject(Meta).getTag('name="robots"')?.content;
}

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
