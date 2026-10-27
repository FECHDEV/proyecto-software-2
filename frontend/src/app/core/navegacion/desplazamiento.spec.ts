import { ViewportScroller } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { provideDesplazamiento } from './desplazamiento';

@Component({ template: '' })
class PantallaVacia {}

// Revisión de HU-10: dónde queda la página al navegar
describe('provideDesplazamiento', () => {
  let router: Router;
  let desplazar: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    desplazar = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: '', component: PantallaVacia },
            { path: 'catalogo', component: PantallaVacia },
            { path: 'producto/:id', component: PantallaVacia },
          ],
        ),
        provideDesplazamiento(),
        {
          provide: ViewportScroller,
          useValue: {
            scrollToPosition: desplazar,
            getScrollPosition: () => [0, 0],
            setHistoryScrollRestoration: () => undefined,
            scrollToAnchor: () => undefined,
            setOffset: () => undefined,
          },
        },
      ],
    });
    router = TestBed.inject(Router);
    await navegar('/');
  });

  async function navegar(url: string): Promise<void> {
    await router.navigateByUrl(url);
  }

  it('otra pantalla empieza arriba', async () => {
    desplazar.mockClear();
    await navegar('/producto/7');
    expect(desplazar).toHaveBeenCalledWith([0, 0]);

    desplazar.mockClear();
    await navegar('/producto/8');
    expect(desplazar).toHaveBeenCalledWith([0, 0]);
  });

  // Revisión total (26/10/2026): el foco pasa al contenido de la pantalla nueva, no con los filtros
  it('otra pantalla lleva el foco al contenido', async () => {
    const contenido = document.createElement('main');
    contenido.id = 'contenido';
    contenido.tabIndex = -1;
    document.body.append(contenido);
    try {
      await navegar('/producto/7');
      expect(document.activeElement).toBe(contenido);

      contenido.blur();
      await navegar('/producto/7?foto=2');
      expect(document.activeElement).not.toBe(contenido);

      // Como al crear un producto: la dirección se reemplaza y el formulario sigue ahí
      await router.navigateByUrl('/producto/8', { replaceUrl: true });
      expect(document.activeElement).not.toBe(contenido);
    } finally {
      contenido.remove();
    }
  });

  it('cambiar los filtros del catálogo no mueve la página', async () => {
    await navegar('/catalogo');
    desplazar.mockClear();
    await navegar('/catalogo?q=pala');
    await navegar('/catalogo?q=pala&pagina=2');

    expect(desplazar).not.toHaveBeenCalled();
  });
});
