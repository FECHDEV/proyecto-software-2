import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CategoriaGestion } from '../modelos/categoria';
import { CategoriasService } from './categorias.service';

// Copia de lo que responde GestionCategoriasController
const JARDIN: CategoriaGestion = {
  idCategoria: 4,
  nombre: 'Jardín',
  descripcion: 'Patio y huerta',
  tieneImagen: false,
  visible: true,
  productos: 0,
};

// HU-06: llamadas a /api/gestion/categorias
describe('CategoriasService', () => {
  let servicio: CategoriasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(CategoriasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-06 RF-2, RF-3
  it('crea con los datos recortados y sin descripción vacía', async () => {
    const promesa = servicio.crear({ nombre: '  Jardín ', descripcion: '   ' });

    const peticion = http.expectOne('/api/gestion/categorias');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ nombre: 'Jardín', descripcion: null });
    peticion.flush(JARDIN, { status: 201, statusText: 'Created' });

    await expect(promesa).resolves.toEqual(JARDIN);
  });

  // HU-06 RF-5
  it('edita por su identificador', async () => {
    const promesa = servicio.editar(4, { nombre: 'Jardín', descripcion: 'Patio' });

    const peticion = http.expectOne('/api/gestion/categorias/4');
    expect(peticion.request.method).toBe('PUT');
    peticion.flush(JARDIN);

    await promesa;
  });

  // HU-06 RF-6: la imagen viaja como multipart, en el campo «imagen»
  it('sube la imagen como multipart', async () => {
    const archivo = new File([new Uint8Array([0x89, 0x50])], 'foto.png', { type: 'image/png' });
    const promesa = servicio.cambiarImagen(4, archivo);

    const peticion = http.expectOne('/api/gestion/categorias/4/imagen');
    expect(peticion.request.method).toBe('POST');
    expect((peticion.request.body as FormData).get('imagen')).toBe(archivo);
    peticion.flush({ ...JARDIN, tieneImagen: true });

    await expect(promesa).resolves.toMatchObject({ tieneImagen: true });
  });

  // HU-06 RF-8
  it('oculta y muestra', async () => {
    const promesa = servicio.cambiarVisibilidad(4, false);

    const peticion = http.expectOne('/api/gestion/categorias/4/visible');
    expect(peticion.request.body).toEqual({ visible: false });
    peticion.flush({ ...JARDIN, visible: false });

    await promesa;
  });

  // HU-06 RF-9
  it('borra', async () => {
    const promesa = servicio.borrar(4);

    const peticion = http.expectOne('/api/gestion/categorias/4');
    expect(peticion.request.method).toBe('DELETE');
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    await promesa;
  });

  // HU-06 RF-4: el 409 llega con su código para el diccionario
  it('rechaza con CATEGORIA_EXISTENTE', async () => {
    const promesa = servicio.crear({ nombre: 'Jardín', descripcion: '' });

    http.expectOne('/api/gestion/categorias').flush(
      { type: 'about:blank', title: 'Conflict', status: 409, detail: 'x', codigo: 'CATEGORIA_EXISTENTE' },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'CATEGORIA_EXISTENTE' });
  });

  // HU-06 RF-11: la dirección pública de la imagen
  it('arma la dirección de la imagen de una categoría', () => {
    expect(servicio.urlDeImagen(4)).toBe('/api/categorias/4/imagen');
  });
});
