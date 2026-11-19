import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Descuento } from '../modelos/descuento';
import { DescuentosService } from './descuentos.service';

// Copia de lo que responde GestionDescuentosController
const RASTRILLOS: Descuento = {
  idDescuento: 3,
  idProducto: 7,
  producto: 'Rastrillo',
  idCategoria: null,
  categoria: null,
  porcentaje: 15,
  inicio: '2026-11-20',
  fin: '2026-11-30',
  estado: 'PROGRAMADO',
};

// HU-24 RF-1 a RF-5: llamadas a /api/gestion/descuentos
describe('DescuentosService', () => {
  let servicio: DescuentosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(DescuentosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-24 RF-3
  it('pide la página', () => {
    const pagina = signal(0);
    TestBed.runInInjectionContext(() => servicio.listar(pagina));
    TestBed.tick();
    http.expectOne('/api/gestion/descuentos').flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });

    pagina.set(2);
    TestBed.tick();
    http.expectOne('/api/gestion/descuentos?pagina=2').flush({ contenido: [], pagina: 2, tamano: 20, totalElementos: 0, totalPaginas: 0 });
  });

  // HU-24 RF-1, RF-4, RF-5
  it('crea, cambia, borra y termina con la dirección y el cuerpo correctos', async () => {
    const guardado = { descuento: RASTRILLOS, bajoElCosto: [], totalBajoElCosto: 0 };
    const solicitud = { idProducto: 7, idCategoria: null, porcentaje: 15, inicio: '2026-11-20', fin: '2026-11-30' };

    const creado = servicio.crear(solicitud);
    const alta = http.expectOne('/api/gestion/descuentos');
    expect(alta.request.method).toBe('POST');
    expect(alta.request.body).toEqual(solicitud);
    alta.flush(guardado, { status: 201, statusText: 'Created' });
    await expect(creado).resolves.toEqual(guardado);

    const cambiado = servicio.cambiar(3, { porcentaje: 20, inicio: '2026-11-21', fin: '2026-11-30' });
    const cambio = http.expectOne('/api/gestion/descuentos/3');
    expect(cambio.request.method).toBe('PUT');
    expect(cambio.request.body).toEqual({ porcentaje: 20, inicio: '2026-11-21', fin: '2026-11-30' });
    cambio.flush(guardado);
    await cambiado;

    const borrado = servicio.borrar(3);
    const baja = http.expectOne('/api/gestion/descuentos/3');
    expect(baja.request.method).toBe('DELETE');
    baja.flush(null, { status: 204, statusText: 'No Content' });
    await borrado;

    const terminado = servicio.terminar(3);
    const fin = http.expectOne('/api/gestion/descuentos/3/terminar');
    expect(fin.request.method).toBe('POST');
    fin.flush(null, { status: 204, statusText: 'No Content' });
    await terminado;
  });
});
