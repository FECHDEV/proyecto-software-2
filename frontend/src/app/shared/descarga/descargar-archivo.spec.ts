import { descargarArchivo } from './descargar-archivo';

describe('descargarArchivo', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('descarga el blob con el nombre dado y después suelta la URL', () => {
    vi.useFakeTimers();
    const blob = new Blob(['%PDF-']);
    URL.createObjectURL = vi.fn(() => 'blob:prueba-1');
    URL.revokeObjectURL = vi.fn();
    let descargado: { href: string; download: string } | undefined;
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      descargado = { href: this.href, download: this.download };
    });

    descargarArchivo(blob, 'reporte-ventas-12.pdf');

    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
    expect(descargado).toEqual({ href: 'blob:prueba-1', download: 'reporte-ventas-12.pdf' });
    // Firefox y Safari empiezan la descarga después del clic: la URL tiene que seguir viva en ese turno
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();
    vi.runAllTimers();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:prueba-1');
  });

  it('no deja el enlace en la página', () => {
    URL.createObjectURL = vi.fn(() => 'blob:prueba-2');
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    descargarArchivo(new Blob(['PK']), 'reporte-ventas-12.xlsx');

    expect(document.querySelector('a[download]')).toBeNull();
  });
});
