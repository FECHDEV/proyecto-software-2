import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { VentasService } from '../../core/api/ventas.service';
import { ErrorApi } from '../../core/modelos/error-api';
import { Comprador, ProductoParaVenta, SolicitudDeVenta, Venta } from '../../core/modelos/venta';
import { VentaPage } from './venta.page';

const MARTILLO: ProductoParaVenta = {
  idProducto: 1,
  nombre: 'Martillo',
  imagen: null,
  precio: 85,
  unidad: 'UNIDAD',
  disponible: 10,
};
const CABLE: ProductoParaVenta = {
  idProducto: 2,
  nombre: 'Cable',
  imagen: null,
  precio: 4.5,
  unidad: 'METRO',
  disponible: 3,
};

function venta(cambios: Partial<Venta> = {}): Venta {
  return {
    idPedido: 31,
    estado: 'ENTREGADO',
    confirmado: '2026-11-05T10:00:00',
    vence: '2026-11-05T10:10:00',
    segundosParaPagar: 0,
    pagado: '2026-11-05T10:00:00',
    comprador: null,
    vendidoPor: 'Eva Paz',
    lineas: [],
    total: 96.25,
    medio: 'EFECTIVO',
    recibido: 100,
    cambio: 3.75,
    cobro: null,
    anulado: null,
    anuladoPor: null,
    motivoAnulacion: null,
    anulable: true,
    ...cambios,
  };
}

const CON_QR = venta({
  estado: 'PENDIENTE_PAGO',
  medio: 'QR',
  recibido: null,
  cambio: null,
  pagado: null,
  segundosParaPagar: 600,
  cobro: { codigoQr: 'qr-de-prueba', urlPasarela: null, simulador: true },
});

class VentasFalso {
  readonly resultados = {
    value: signal<ProductoParaVenta[] | undefined>([MARTILLO, CABLE]),
    error: signal<unknown>(undefined),
    hasValue(): boolean {
      return this.value() !== undefined;
    },
  };
  readonly registrar = vi.fn((_solicitud: SolicitudDeVenta): Promise<Venta> => Promise.resolve(venta()));
  readonly consultar = vi.fn((_id: number): Promise<Venta> => Promise.resolve(CON_QR));
  readonly cancelar = vi.fn((_id: number): Promise<Venta> => Promise.resolve({ ...CON_QR, estado: 'CANCELADO' }));
  readonly simular = vi.fn((): Promise<void> => Promise.resolve());
  readonly buscarCliente = vi.fn(
    (_correo: string): Promise<Comprador> => Promise.resolve({ idUsuario: 4, nombre: 'Ana Paz', correo: 'ana@mail.com' }),
  );
  readonly precioDeHoy = vi.fn((_id: number): Promise<number> => Promise.resolve(90));
  productos() {
    return this.resultados;
  }
}

// HU-23: «Nueva venta», armar la venta del mostrador y cobrarla
describe('VentaPage', () => {
  let harness: RouterTestingHarness;
  let ventas: VentasFalso;

  async function abrir(): Promise<void> {
    TestBed.resetTestingModule();
    ventas = new VentasFalso();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'ventas/nueva', component: VentaPage },
          { path: 'ventas/:id', children: [] },
        ]),
        { provide: VentasService, useValue: ventas },
      ],
    });
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/ventas/nueva');
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  const pantalla = (): HTMLElement => harness.routeNativeElement!;
  const lineas = (): HTMLElement[] => Array.from(pantalla().querySelectorAll<HTMLElement>('.linea'));
  const boton = (texto: string): HTMLButtonElement =>
    Array.from(pantalla().querySelectorAll<HTMLButtonElement>('button')).find(
      (cada) => cada.textContent?.trim() === texto,
    )!;

  async function tocarProducto(nombre: string): Promise<void> {
    const resultado = Array.from(pantalla().querySelectorAll<HTMLButtonElement>('.resultado')).find((cada) =>
      cada.textContent?.includes(nombre),
    )!;
    resultado.click();
    await harness.fixture.whenStable();
  }

  async function escribirCantidad(indice: number, texto: string): Promise<void> {
    const campo = lineas()[indice].querySelector<HTMLInputElement>('input')!;
    campo.value = texto;
    campo.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  async function escribirRecibido(texto: string): Promise<void> {
    const campo = pantalla().querySelector<HTMLInputElement>('#recibido')!;
    campo.value = texto;
    campo.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
  }

  async function tocar(texto: string): Promise<void> {
    boton(texto).click();
    await harness.fixture.whenStable();
  }

  // HU-23 RF-2: el mismo producto suma uno a su línea; otro, una línea nueva
  it('agregaYSumaUnaLineaPorProducto', async () => {
    await abrir();

    await tocarProducto('Martillo');
    await tocarProducto('Martillo');
    await tocarProducto('Cable');

    expect(lineas()).toHaveLength(2);
    expect(lineas()[0].querySelector<HTMLInputElement>('input')!.value).toBe('2');
    expect(lineas()[0].querySelector('.subtotal')?.textContent).toContain('170');
    expect(pantalla().querySelector('.total')?.textContent).toContain('174,50');
  });

  // HU-23 RF-3, RF-2: la línea que pide más de lo disponible se marca; los decimales según la unidad
  it('marcaLaLineaQuePasaDelDisponible', async () => {
    await abrir();
    await tocarProducto('Cable');

    await escribirCantidad(0, '3,5');
    expect(lineas()[0].classList).toContain('marcada');
    expect(lineas()[0].querySelector('.aviso-linea')?.textContent).toContain('hay 3 metros');

    await escribirCantidad(0, '2,5');
    expect(lineas()[0].classList).not.toContain('marcada');

    await tocarProducto('Martillo');
    await escribirCantidad(1, '1,5');
    expect(lineas()[1].querySelector('.aviso-linea')?.textContent).toContain('enteros');
    expect(boton('Cobrar en efectivo').getAttribute('aria-disabled')).toBe('true');
  });

  // HU-23 RF-4: consumidor final de entrada; con el correo, la cuenta del Cliente
  it('buscaAlCompradorPorCorreo', async () => {
    await abrir();
    expect(pantalla().querySelector('.comprador')?.textContent).toContain('Consumidor final');
    const correo = pantalla().querySelector<HTMLInputElement>('#correo-comprador')!;
    correo.value = ' ana@mail.com ';
    correo.dispatchEvent(new Event('input'));

    await tocar('Buscar');

    expect(ventas.buscarCliente).toHaveBeenCalledWith('ana@mail.com');
    expect(pantalla().querySelector('.comprador')?.textContent).toContain('Ana Paz');
    await tocarProducto('Martillo');
    await escribirRecibido('100');
    await tocar('Cobrar en efectivo');
    expect(ventas.registrar.mock.calls[0][0].idCliente).toBe(4);

    ventas.buscarCliente.mockRejectedValueOnce({ status: 404, detail: 'x', codigo: 'USUARIO_NO_ENCONTRADO' });
    await abrir();
    ventas.buscarCliente.mockRejectedValueOnce({ status: 404, detail: 'x', codigo: 'USUARIO_NO_ENCONTRADO' });
    const otro = pantalla().querySelector<HTMLInputElement>('#correo-comprador')!;
    otro.value = 'x@mail.com';
    otro.dispatchEvent(new Event('input'));
    await tocar('Buscar');
    expect(pantalla().querySelector('#error-comprador')?.textContent).toContain('No hay una cuenta de cliente activa');
  });

  // HU-23 RF-5: el cambio en vivo; con menos de lo debido no se registra
  it('calculaElCambioYNoDejaCobrarMenos', async () => {
    await abrir();
    await tocarProducto('Martillo');
    await tocarProducto('Cable');
    await escribirCantidad(1, '2,5');

    await escribirRecibido('100');
    expect(pantalla().querySelector('#cambio')?.textContent).toContain('3,75');

    await escribirRecibido('90');
    await tocar('Cobrar en efectivo');
    expect(pantalla().querySelector('#error-recibido')?.textContent).toContain('no alcanza');
    expect(ventas.registrar).not.toHaveBeenCalled();
  });

  // HU-23 RF-6: en efectivo registra con el total que se vio y va al comprobante
  it('enEfectivoVaAlComprobante', async () => {
    await abrir();
    await tocarProducto('Martillo');
    await tocarProducto('Cable');
    await escribirCantidad(1, '2,5');
    await escribirRecibido('100');

    await tocar('Cobrar en efectivo');

    expect(ventas.registrar).toHaveBeenCalledWith({
      lineas: [
        { idProducto: 1, cantidad: 1 },
        { idProducto: 2, cantidad: 2.5 },
      ],
      idCliente: null,
      medio: 'EFECTIVO',
      recibido: 100,
      totalEsperado: 96.25,
    });
    expect(TestBed.inject(Router).url).toBe('/ventas/31');
  });

  // HU-23 RF-7, RF-8: con QR muestra el código; «Ya pagó» consulta y, pagada, va al comprobante
  it('conQrMuestraElQrYConsulta', async () => {
    await abrir();
    ventas.registrar.mockResolvedValueOnce(CON_QR);
    await tocarProducto('Martillo');

    await tocar('Cobrar con QR');

    expect(ventas.registrar.mock.calls[0][0]).toMatchObject({ medio: 'QR', recibido: null });
    expect(pantalla().querySelector('app-codigo-qr')).not.toBeNull();
    expect(pantalla().querySelector('.tiempo')?.textContent).toContain('10:00');
    // Con el QR a la vista, la venta no se cambia
    expect(lineas()[0].querySelector('.quitar')?.getAttribute('aria-disabled')).toBe('true');

    await tocar('Ya pagó');
    expect(ventas.consultar).toHaveBeenCalledWith(31);
    expect(pantalla().querySelector('.estado')?.textContent).toContain('Todavía no llegó el pago');

    ventas.consultar.mockResolvedValueOnce(venta({ medio: 'QR' }));
    await tocar('Ya pagó');
    expect(TestBed.inject(Router).url).toBe('/ventas/31');
  });

  // HU-23 RF-9: cancelar el QR deja las mismas líneas para cobrar de otra forma
  it('cancelarDejaLasLineas', async () => {
    await abrir();
    ventas.registrar.mockResolvedValueOnce(CON_QR);
    await tocarProducto('Martillo');
    await tocar('Cobrar con QR');

    await tocar('Cancelar venta');

    expect(ventas.cancelar).toHaveBeenCalledWith(31);
    expect(pantalla().querySelector('app-codigo-qr')).toBeNull();
    expect(lineas()).toHaveLength(1);
    expect(boton('Cobrar en efectivo').getAttribute('aria-disabled')).toBeNull();
    expect(pantalla().querySelector('.aviso')?.textContent).toContain('Se canceló el cobro con QR');
  });

  // HU-23 RF-12, RF-14: cada línea rechazada dice por qué y cuánto hay; si cambió un precio, se trae el de hoy
  it('marcaLasLineasRechazadas', async () => {
    await abrir();
    await tocarProducto('Martillo');
    await tocarProducto('Cable');
    await escribirRecibido('200');
    const rechazo: ErrorApi = {
      status: 409,
      detail: 'x',
      codigo: 'STOCK_INSUFICIENTE',
      lineas: [{ idProducto: 2, motivo: 'NO_ALCANZA', disponible: 0.5 }],
    };
    ventas.registrar.mockRejectedValueOnce(rechazo);

    await tocar('Cobrar en efectivo');

    expect(lineas()[1].classList).toContain('marcada');
    expect(lineas()[1].querySelector('.aviso-linea')?.textContent).toContain('hay 0,5 metros');
    expect(pantalla().querySelector('.aviso')?.textContent).toContain('Corrija los marcados');

    ventas.registrar.mockRejectedValueOnce({ status: 409, detail: 'x', codigo: 'TOTAL_CAMBIADO', total: 94.5 });
    await escribirCantidad(1, '0,5');
    await tocar('Cobrar en efectivo');
    await harness.fixture.whenStable();
    expect(ventas.precioDeHoy).toHaveBeenCalledTimes(2);
    expect(lineas()[0].querySelector('.precio')?.textContent).toContain('90');
    expect(pantalla().querySelector('.aviso')?.textContent).toContain('Cambió un precio');
  });

  // HU-23 (casos límite): si el sistema de pago no generó el QR, la venta ya se canceló y las líneas siguen
  it('siNoSeGeneraElQrQuedanLasLineas', async () => {
    await abrir();
    ventas.registrar.mockRejectedValueOnce({ status: 503, detail: 'x', codigo: 'COBRO_NO_DISPONIBLE' });
    await tocarProducto('Martillo');

    await tocar('Cobrar con QR');

    expect(pantalla().querySelector('app-codigo-qr')).toBeNull();
    expect(lineas()).toHaveLength(1);
    expect(pantalla().querySelector('.aviso')?.textContent).toContain('No pudimos generar el cobro');
  });

  // HU-23 (casos límite): un segundo toque mientras espera no registra dos veces
  it('unSegundoToqueNoRegistraDosVeces', async () => {
    await abrir();
    let terminar!: (venta: Venta) => void;
    ventas.registrar.mockReturnValueOnce(new Promise((resuelve) => (terminar = resuelve)));
    await tocarProducto('Martillo');
    await escribirRecibido('100');

    boton('Cobrar en efectivo').click();
    boton('Cobrar en efectivo').click();
    await harness.fixture.whenStable().catch(() => undefined);

    expect(ventas.registrar).toHaveBeenCalledTimes(1);
    terminar(venta());
    await harness.fixture.whenStable();
  });
});
