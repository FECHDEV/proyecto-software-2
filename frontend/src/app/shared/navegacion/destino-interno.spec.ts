import { destinoInterno, parametrosDelDestino } from './destino-interno';

describe('destinoInterno', () => {
  it('acepta una ruta del sitio, con sus parámetros', () => {
    expect(destinoInterno('/catalogo/5/entradas/1')).toBe('/catalogo/5/entradas/1');
    expect(destinoInterno('/eventos?estado=ACTIVO')).toBe('/eventos?estado=ACTIVO');
  });

  // Un destino externo sería un redirect abierto
  it.each(['//sitio-ajeno.example/phishing', 'https://sitio-ajeno.example', 'catalogo/5', ''])(
    'descarta «%s»',
    (pedido) => {
      expect(destinoInterno(pedido)).toBeNull();
    },
  );

  it('sin destino devuelve null', () => {
    expect(destinoInterno(null)).toBeNull();
  });
});

describe('parametrosDelDestino', () => {
  it('pasa solo un destino del sitio', () => {
    expect(parametrosDelDestino('/reservas/40')).toEqual({ destino: '/reservas/40' });
    expect(parametrosDelDestino('//sitio-ajeno.example')).toEqual({});
    expect(parametrosDelDestino(null)).toEqual({});
  });
});
