import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DatosDelTicket, Ticket } from './ticket';

const DATOS: DatosDelTicket = {
  marca: 'Tienda Montero',
  titulo: 'Pedido N.º 12',
  fecha: '17/11/2026 10:30',
  estado: null,
  datos: [
    { etiqueta: 'Cliente', valor: 'Ana Paz' },
    { etiqueta: 'Atendió', valor: 'Eva Suárez' },
  ],
  lineas: [{ nombre: 'Martillo', detalle: '2 unidades × Bs 85,50', importe: 'Bs 171,00' }],
  total: 'Bs 171,00',
  pagos: [
    { etiqueta: 'Pago', valor: 'Efectivo' },
    { etiqueta: 'Recibido', valor: 'Bs 200,00' },
  ],
  anulacion: null,
};

// El ticket del comprobante (HU-23 RF-15, HU-14 RF-10): dibuja lo que recibe, ya escrito
describe('Ticket', () => {
  async function dibujar(datos: DatosDelTicket): Promise<ComponentFixture<Ticket>> {
    const fijo = TestBed.createComponent(Ticket);
    fijo.componentRef.setInput('datos', datos);
    fijo.autoDetectChanges();
    await fijo.whenStable();
    return fijo;
  }

  const texto = (fijo: ComponentFixture<Ticket>): string =>
    (fijo.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ') ?? '';

  const fila = (fijo: ComponentFixture<Ticket>, rotulo: string): string | undefined =>
    Array.from((fijo.nativeElement as HTMLElement).querySelectorAll('tfoot tr'))
      .find((cada) => cada.querySelector('th')?.textContent?.trim() === rotulo)
      ?.querySelector('td')
      ?.textContent?.trim();

  const dato = (fijo: ComponentFixture<Ticket>, rotulo: string): string | undefined =>
    Array.from((fijo.nativeElement as HTMLElement).querySelectorAll('.datos div'))
      .find((cada) => cada.querySelector('dt')?.textContent?.trim() === rotulo)
      ?.querySelector('dd')
      ?.textContent?.trim();

  it('dibuja los datos, las líneas, el total y los pagos que recibe', async () => {
    const fijo = await dibujar(DATOS);

    expect(texto(fijo)).toContain('Tienda Montero');
    expect(fijo.nativeElement.querySelector('h1#titulo-ticket')?.textContent).toContain('Pedido N.º 12');
    expect(texto(fijo)).toContain('17/11/2026 10:30');
    expect(dato(fijo, 'Cliente')).toBe('Ana Paz');
    expect(dato(fijo, 'Atendió')).toBe('Eva Suárez');
    expect(texto(fijo)).toContain('Martillo');
    expect(texto(fijo)).toContain('2 unidades × Bs 85,50');
    expect(fila(fijo, 'Total')).toBe('Bs 171,00');
    expect(fila(fijo, 'Pago')).toBe('Efectivo');
    expect(fila(fijo, 'Recibido')).toBe('Bs 200,00');
  });

  it('sin estado no muestra el chip; con anulación la muestra', async () => {
    const sinEstado = await dibujar(DATOS);
    expect(sinEstado.nativeElement.querySelector('.estado')).toBeNull();
    expect(sinEstado.nativeElement.querySelector('.anulacion')).toBeNull();

    const anulada = await dibujar({
      ...DATOS,
      estado: { texto: 'Anulado', tono: 'tono-neutro' },
      anulacion: 'Anulada el 17/11/2026 11:00 por Ana Paz: Error de cobro',
    });
    expect(anulada.nativeElement.querySelector('.estado')?.textContent?.trim()).toBe('Anulado');
    expect(anulada.nativeElement.querySelector('.estado')?.classList).toContain('tono-neutro');
    expect(anulada.nativeElement.querySelector('.anulacion')?.textContent).toContain('Error de cobro');
  });

  it('siempre dice «No válido como factura»', async () => {
    const fijo = await dibujar(DATOS);

    expect(fijo.nativeElement.querySelector('.leyenda')?.textContent?.trim()).toBe('No válido como factura');
  });
});
