import { idDeLaUrl } from './id-de-la-url';

// Los ids del backend son Long autoincrementales: empiezan en 1. Lo que no es un
// id no llega al backend
describe('idDeLaUrl', () => {
  it('lee un id válido', () => {
    expect(idDeLaUrl('5')).toBe(5);
    expect(idDeLaUrl('123456789012345')).toBe(123456789012345);
  });

  it.each([null, '', '0', '-1', 'abc', '5a', '1e3', '2.5', ' 5', '0005', '1234567890123456'])(
    '«%s» no es un id',
    (valor) => {
      expect(idDeLaUrl(valor)).toBeNull();
    },
  );
});
