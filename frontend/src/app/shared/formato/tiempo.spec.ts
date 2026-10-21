import { comoReloj } from './tiempo';

// HU-12 RF-8 y HU-14 RF-2: el tiempo para pagar
describe('comoReloj', () => {
  it('con dos cifras en los minutos y en los segundos', () => {
    expect(comoReloj(1795)).toBe('29:55');
    expect(comoReloj(65)).toBe('01:05');
    expect(comoReloj(0)).toBe('00:00');
  });

  it('nunca negativo ni con decimales', () => {
    expect(comoReloj(-4)).toBe('00:00');
    expect(comoReloj(9.7)).toBe('00:09');
  });
});
