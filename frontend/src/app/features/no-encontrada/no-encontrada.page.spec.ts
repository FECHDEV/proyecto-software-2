import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { NoEncontradaPage } from './no-encontrada.page';

// Revisión total (27/10/2026)
describe('NoEncontradaPage', () => {
  it('dice que la página no existe y ofrece el catálogo y el inicio', async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', component: NoEncontradaPage }])],
    });
    const harness = await RouterTestingHarness.create('/no-existe');
    const pantalla = harness.routeNativeElement as HTMLElement;

    expect(pantalla.querySelector('h1')?.textContent).toContain('Esta página no existe');
    const enlaces = Array.from(pantalla.querySelectorAll('a')).map((enlace) => enlace.getAttribute('href'));
    expect(enlaces).toEqual(['/catalogo', '/']);
  });
});
