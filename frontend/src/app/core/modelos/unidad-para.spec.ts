import { unidadPara } from './producto';

// Revisión total (26/10/2026): «40 unidades», no «40 unidad»
describe('unidadPara', () => {
  it('va en singular solo con 1', () => {
    expect(unidadPara('UNIDAD', 1)).toBe('unidad');
    expect(unidadPara('UNIDAD', 40)).toBe('unidades');
    expect(unidadPara('UNIDAD', 0)).toBe('unidades');
    expect(unidadPara('METRO', 2.5)).toBe('metros');
    expect(unidadPara('PAR', 3)).toBe('pares');
  });
});
