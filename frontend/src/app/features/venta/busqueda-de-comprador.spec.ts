import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { VentasService } from '../../core/api/ventas.service';
import { Comprador, CompradoresEncontrados } from '../../core/modelos/venta';
import { BusquedaDeComprador } from './busqueda-de-comprador';

const ANA: Comprador = { idUsuario: 4, nombre: 'Ana Paz', correo: 'an***@mail.com' };
const ANA_POR_CORREO: Comprador = { idUsuario: 4, nombre: 'Ana Paz', correo: 'ana@mail.com' };
const EVA: Comprador = { idUsuario: 7, nombre: 'Ana Eva Rojas', correo: 'ev***@mail.com' };

class VentasFalso {
  readonly buscarClientesPorNombre = vi.fn(
    (_nombre: string): Promise<CompradoresEncontrados> => Promise.resolve({ cuentas: [ANA, EVA], hayMas: false }),
  );
  readonly buscarCliente = vi.fn((_correo: string): Promise<Comprador> => Promise.resolve(ANA_POR_CORREO));
}

// HU-23 RF-25 a RF-29 (ampliación del 18/11/2026): el comprador por nombre, apellido o correo
describe('BusquedaDeComprador', () => {
  let fixture: ComponentFixture<BusquedaDeComprador>;
  let ventas: VentasFalso;
  let elegidos: Comprador[];

  async function crear(): Promise<void> {
    ventas = new VentasFalso();
    TestBed.configureTestingModule({ providers: [{ provide: VentasService, useValue: ventas }] });
    fixture = TestBed.createComponent(BusquedaDeComprador);
    elegidos = [];
    fixture.componentInstance.elegir.subscribe((cuenta) => elegidos.push(cuenta));
    fixture.autoDetectChanges();
    await fixture.whenStable();
  }

  const elemento = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const resultados = (): HTMLButtonElement[] => Array.from(elemento().querySelectorAll<HTMLButtonElement>('.resultado'));
  const mensaje = (): string => elemento().querySelector('.mensaje')?.textContent?.trim() ?? '';
  const campo = (): HTMLInputElement => elemento().querySelector<HTMLInputElement>('#texto-comprador')!;

  function escribir(texto: string): void {
    campo().value = texto;
    campo().dispatchEvent(new Event('input'));
  }

  async function buscar(texto: string): Promise<void> {
    escribir(texto);
    elemento().querySelector<HTMLButtonElement>('button[type="submit"]')!.click();
    await fixture.whenStable();
  }

  // HU-23 RF-25, RF-26
  it('RF-25, RF-26: sin «@» lista las cuentas con el nombre y el correo enmascarado', async () => {
    await crear();

    await buscar('  ana ');

    expect(ventas.buscarClientesPorNombre).toHaveBeenCalledWith('ana');
    expect(ventas.buscarCliente).not.toHaveBeenCalled();
    expect(
      resultados().map((cada) => [cada.querySelector('.nombre')?.textContent, cada.querySelector('.correo')?.textContent]),
    ).toEqual([
      ['Ana Paz', 'an***@mail.com'],
      ['Ana Eva Rojas', 'ev***@mail.com'],
    ]);
    expect(elemento().querySelector('[aria-live]')?.textContent).toContain('2 cuentas');
  });

  // Revisión de interfaz: mientras busca, lo dice en la misma región que anuncia el resultado
  it('mientras busca dice «Buscando clientes…»', async () => {
    await crear();
    let responder: (encontrados: CompradoresEncontrados) => void = () => undefined;
    ventas.buscarClientesPorNombre.mockImplementationOnce(
      () => new Promise<CompradoresEncontrados>((resolver) => (responder = resolver)),
    );

    await buscar('ana');
    expect(mensaje()).toBe('Buscando clientes…');
    expect(elemento().querySelector('button[type="submit"]')?.getAttribute('aria-disabled')).toBeNull();

    responder({ cuentas: [ANA], hayMas: false });
    await fixture.whenStable();
    expect(resultados()).toHaveLength(1);
  });

  // HU-23 RF-27
  it('RF-27: elegir una emite su cuenta', async () => {
    await crear();
    await buscar('ana');

    resultados()[1].click();
    await fixture.whenStable();

    expect(elegidos).toEqual([EVA]);
    expect(resultados()).toHaveLength(0);
  });

  // Revisión de interfaz y /code-review: al cambiar o borrar el texto se va la lista de la búsqueda anterior
  it('al cambiar o borrar el texto se va la lista', async () => {
    await crear();
    await buscar('ana');

    escribir('eva');
    await fixture.whenStable();
    expect(resultados()).toHaveLength(0);

    await buscar('ana');
    escribir('');
    await fixture.whenStable();
    expect(resultados()).toHaveLength(0);
  });

  // /code-review: cambiar el texto o una búsqueda que se corta descartan la respuesta que estaba por llegar
  it('una respuesta que llega después de cambiar el texto no se aplica', async () => {
    await crear();
    let responder: (encontrados: CompradoresEncontrados) => void = () => undefined;
    ventas.buscarClientesPorNombre.mockImplementation(
      () => new Promise<CompradoresEncontrados>((resolver) => (responder = resolver)),
    );

    await buscar('ana');
    escribir('pedro');
    responder({ cuentas: [ANA], hayMas: false });
    await fixture.whenStable();
    expect(resultados()).toHaveLength(0);
    expect(mensaje()).toBe('');

    await buscar('ana');
    await buscar('an');
    responder({ cuentas: [ANA], hayMas: false });
    await fixture.whenStable();
    expect(resultados()).toHaveLength(0);
    expect(elemento().querySelector('#error-comprador')?.textContent).toContain('al menos 3 letras');
  });

  // /code-review: una lista por nombre que llega con la venta bloqueada no se guarda ni se anuncia
  it('una respuesta por nombre que llega con la venta bloqueada no se aplica', async () => {
    await crear();
    let responder: (encontrados: CompradoresEncontrados) => void = () => undefined;
    ventas.buscarClientesPorNombre.mockImplementationOnce(
      () => new Promise<CompradoresEncontrados>((resolver) => (responder = resolver)),
    );

    await buscar('ana');
    fixture.componentRef.setInput('bloqueada', true);
    responder({ cuentas: [ANA], hayMas: false });
    await fixture.whenStable();
    fixture.componentRef.setInput('bloqueada', false);
    await fixture.whenStable();

    expect(resultados()).toHaveLength(0);
    expect(mensaje()).toBe('');
  });

  // /code-review: si se cobró con QR mientras llegaba la respuesta del correo, el comprador no cambia (RF-27)
  it('una respuesta por correo que llega con la venta bloqueada no elige', async () => {
    await crear();
    let responder: (cuenta: Comprador) => void = () => undefined;
    ventas.buscarCliente.mockImplementationOnce(() => new Promise<Comprador>((resolver) => (responder = resolver)));

    await buscar('ana@mail.com');
    fixture.componentRef.setInput('bloqueada', true);
    responder(ANA_POR_CORREO);
    await fixture.whenStable();

    expect(elegidos).toEqual([]);
  });

  // RF-29: las letras se cuentan como en el servidor, sin tildes sueltas ni espacios
  it('RF-29: una tilde suelta no cuenta como letra', async () => {
    await crear();

    await buscar('Je\u0301');

    expect(ventas.buscarClientesPorNombre).not.toHaveBeenCalled();
    expect(elemento().querySelector('#error-comprador')?.textContent).toContain('al menos 3 letras');
  });

  // HU-23 RF-4, RF-28
  it('RF-4, RF-28: con «@» busca por correo exacto y elige directo; un 404 dice que no hay cuenta con ese correo', async () => {
    await crear();

    await buscar(' ana@mail.com ');

    expect(ventas.buscarCliente).toHaveBeenCalledWith('ana@mail.com');
    expect(ventas.buscarClientesPorNombre).not.toHaveBeenCalled();
    expect(elegidos).toEqual([ANA_POR_CORREO]);

    ventas.buscarCliente.mockRejectedValueOnce({ status: 404, detail: 'x', codigo: 'USUARIO_NO_ENCONTRADO' });
    await buscar('x@mail.com');
    expect(elemento().querySelector('#error-comprador')?.textContent).toContain(
      'No hay una cuenta de cliente activa con ese correo',
    );
    expect(campo().getAttribute('aria-invalid')).toBe('true');
  });

  // HU-23 RF-29
  it('RF-29: con menos de 3 letras avisa y no llama a la API', async () => {
    await crear();

    await buscar('a b');

    expect(ventas.buscarClientesPorNombre).not.toHaveBeenCalled();
    expect(elemento().querySelector('#error-comprador')?.textContent).toContain('al menos 3 letras');

    await buscar('   ');
    expect(ventas.buscarClientesPorNombre).not.toHaveBeenCalled();
    expect(elemento().querySelector('#error-comprador')?.textContent).toContain('Escriba');
  });

  // HU-23 RF-29
  it('RF-29: sin resultados lo dice', async () => {
    await crear();
    ventas.buscarClientesPorNombre.mockResolvedValueOnce({ cuentas: [], hayMas: false });

    await buscar('zzyzx');

    expect(resultados()).toHaveLength(0);
    expect(mensaje()).toContain('No hay una cuenta de cliente activa con ese nombre');
  });

  // Duda 4 de la ampliación: con más de 10, que escriba el apellido
  it('con más de 10 avisa que escriba el apellido', async () => {
    await crear();
    ventas.buscarClientesPorNombre.mockResolvedValueOnce({ cuentas: [ANA, EVA], hayMas: true });

    await buscar('ana');

    expect(resultados()).toHaveLength(2);
    expect(mensaje()).toContain('Hay más cuentas con ese nombre: escriba el apellido');
  });

  // Casos límite: una respuesta tardía de una búsqueda anterior no reemplaza la última
  it('una respuesta tardía de una búsqueda anterior no reemplaza la última', async () => {
    await crear();
    let responderPrimera: (encontrados: CompradoresEncontrados) => void = () => undefined;
    ventas.buscarClientesPorNombre.mockImplementationOnce(
      () => new Promise<CompradoresEncontrados>((resolver) => (responderPrimera = resolver)),
    );
    ventas.buscarClientesPorNombre.mockResolvedValueOnce({ cuentas: [EVA], hayMas: false });

    await buscar('ana');
    await buscar('eva');
    responderPrimera({ cuentas: [ANA], hayMas: false });
    await fixture.whenStable();

    expect(resultados().map((cada) => cada.textContent)).toEqual([expect.stringContaining('Ana Eva Rojas')]);
  });

  // HU-23 RF-27 y casos límite: con el QR a la vista o mientras se registra no se cambia el comprador
  it('bloqueada no busca ni muestra la lista', async () => {
    await crear();
    await buscar('ana');
    fixture.componentRef.setInput('bloqueada', true);
    await fixture.whenStable();

    expect(resultados()).toHaveLength(0);
    expect(campo().readOnly).toBe(true);
    await buscar('eva');

    expect(elegidos).toEqual([]);
    expect(ventas.buscarClientesPorNombre).toHaveBeenCalledTimes(1);
  });

  // Casos límite: un nombre con HTML se muestra como texto
  it('un nombre con HTML se muestra como texto', async () => {
    await crear();
    ventas.buscarClientesPorNombre.mockResolvedValueOnce({
      cuentas: [{ idUsuario: 9, nombre: '<b>Ana</b>', correo: 'an***@mail.com' }],
      hayMas: false,
    });

    await buscar('ana');

    expect(resultados()[0].querySelector('b')).toBeNull();
    expect(resultados()[0].textContent).toContain('<b>Ana</b>');
  });
});
