import { aTarjeta, ProductoEnTienda, srcsetDeImagen, urlDeImagen } from './catalogo';

// HU-22: las direcciones de las versiones achicadas de una foto
describe('urlDeImagen', () => {
  it('sin ancho es el original; con ancho, su versión', () => {
    expect(urlDeImagen(7, 'a b.png')).toBe('/api/productos/7/imagenes/a%20b.png');
    expect(urlDeImagen(7, 'a.png', 400)).toBe('/api/productos/7/imagenes/a.png?ancho=400');
    expect(urlDeImagen(7, 'a.png', 1200)).toBe('/api/productos/7/imagenes/a.png?ancho=1200');
  });

  it('el srcset lleva las dos versiones con su ancho', () => {
    expect(srcsetDeImagen(7, 'a.png')).toBe(
      '/api/productos/7/imagenes/a.png?ancho=400 400w, /api/productos/7/imagenes/a.png?ancho=1200 1200w',
    );
  });

  // RF-11: la tarjeta usa la de 400
  it('la tarjeta pide la de 400', () => {
    const producto = { idProducto: 7, imagenPrincipal: 'a.png' } as ProductoEnTienda;

    expect(aTarjeta({ ...producto, unidad: 'UNIDAD' }).imagen).toBe('/api/productos/7/imagenes/a.png?ancho=400');
  });
});
