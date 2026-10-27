import { centrarEnLaFila } from './centrar-en-la-fila';

// Revisión total (26/10/2026): la sección actual a la vista en la navegación del celular
describe('centrarEnLaFila', () => {
  function caja(medidas: Partial<HTMLElement>): HTMLElement {
    return { scrollLeft: 0, offsetLeft: 0, clientWidth: 0, offsetWidth: 0, ...medidas } as HTMLElement;
  }

  it('lleva el elemento al centro de la fila', () => {
    const fila = caja({ offsetLeft: 0, clientWidth: 300 });

    centrarEnLaFila(fila, caja({ offsetLeft: 500, offsetWidth: 100 }));

    expect(fila.scrollLeft).toBe(400);
  });

  it('un elemento del principio deja la fila al comienzo', () => {
    const fila = caja({ offsetLeft: 0, clientWidth: 300 });

    centrarEnLaFila(fila, caja({ offsetLeft: 20, offsetWidth: 100 }));

    expect(fila.scrollLeft).toBe(0);
  });
});
