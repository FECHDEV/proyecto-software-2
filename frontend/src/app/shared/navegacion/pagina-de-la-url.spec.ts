import { paginaDeLaUrl } from './pagina-de-la-url';

// La URL la puede escribir cualquiera: solo dígitos y un tamaño que entre en el
// int del backend son una página; lo demás es la primera
describe('paginaDeLaUrl', () => {
  it('lee una página válida', () => {
    expect(paginaDeLaUrl('3')).toBe(3);
    expect(paginaDeLaUrl('0')).toBe(0);
  });

  it('sin página es la primera', () => {
    expect(paginaDeLaUrl(null)).toBe(0);
    expect(paginaDeLaUrl('')).toBe(0);
  });

  it.each(['-1', 'abc', '1e20', '2.5', ' 2', '1234567'])('«%s» no es una página', (valor) => {
    expect(paginaDeLaUrl(valor)).toBe(0);
  });
});
