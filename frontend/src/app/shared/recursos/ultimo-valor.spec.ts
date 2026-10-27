import { Resource, signal } from '@angular/core';
import { ultimoValor } from './ultimo-valor';

// Revisión total (27/10/2026)
describe('ultimoValor', () => {
  function recursoFalso() {
    const valor = signal<string | undefined>(undefined);
    const error = signal<Error | undefined>(undefined);
    const recurso = {
      value: valor,
      error,
      hasValue: () => valor() !== undefined && error() === undefined,
    } as unknown as Resource<string | undefined>;
    return { recurso, valor, error };
  }

  it('mientras carga otra página queda la que se veía', () => {
    const { recurso, valor } = recursoFalso();
    const ultimo = ultimoValor(recurso);
    expect(ultimo()).toBeUndefined();

    valor.set('página 1');
    expect(ultimo()).toBe('página 1');

    valor.set(undefined);
    expect(ultimo()).toBe('página 1');

    valor.set('página 2');
    expect(ultimo()).toBe('página 2');
  });

  it('tras un error no queda nada, ni al reintentar', () => {
    const { recurso, valor, error } = recursoFalso();
    const ultimo = ultimoValor(recurso);
    valor.set('búsqueda A');
    expect(ultimo()).toBe('búsqueda A');

    valor.set(undefined);
    error.set(new Error('sin conexión'));
    expect(ultimo()).toBeUndefined();

    error.set(undefined);
    expect(ultimo()).toBeUndefined();

    valor.set('búsqueda B');
    expect(ultimo()).toBe('búsqueda B');
  });
});
