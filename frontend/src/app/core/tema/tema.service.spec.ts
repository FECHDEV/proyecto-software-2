import { TestBed } from '@angular/core/testing';

import { CLAVE_TEMA, guardarTema, leerTema } from './almacen-tema';
import { TemaService } from './tema.service';

describe('almacén del tema', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => vi.restoreAllMocks());

  // decisiones.md → Sistema de diseño: el oscuro es el modo por defecto
  it('sin nada guardado, el tema es oscuro', () => {
    expect(leerTema()).toBe('oscuro');
  });

  it('recupera el tema guardado', () => {
    guardarTema('claro');

    expect(leerTema()).toBe('claro');
    expect(localStorage.getItem(CLAVE_TEMA)).toBe('claro');
  });

  it('un valor desconocido cuenta como oscuro', () => {
    localStorage.setItem(CLAVE_TEMA, 'rosado');

    expect(leerTema()).toBe('oscuro');
  });

  // Modo privado o almacenamiento bloqueado: el tema vive solo en memoria
  it('si el almacenamiento falla, no rompe y usa oscuro', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });

    expect(() => guardarTema('claro')).not.toThrow();
    expect(leerTema()).toBe('oscuro');
  });
});

describe('TemaService', () => {
  let meta: HTMLMetaElement;

  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset['tema'];
    meta = document.createElement('meta');
    meta.name = 'theme-color';
    meta.content = '#0b1020';
    document.head.appendChild(meta);
    TestBed.resetTestingModule();
  });

  afterEach(() => meta.remove());

  function crear(): TemaService {
    const servicio = TestBed.inject(TemaService);
    TestBed.tick();
    return servicio;
  }

  it('empieza en oscuro, sin marcar el documento', () => {
    const servicio = crear();

    expect(servicio.tema()).toBe('oscuro');
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(meta.content).toBe('#0b1020');
  });

  it('alternar pasa a claro, lo aplica al documento y lo recuerda', () => {
    const servicio = crear();

    servicio.alternar();
    TestBed.tick();

    expect(servicio.tema()).toBe('claro');
    expect(document.documentElement.dataset['tema']).toBe('claro');
    expect(meta.content).toBe('#f6f7fb');
    expect(localStorage.getItem(CLAVE_TEMA)).toBe('claro');
  });

  it('alternar dos veces vuelve a oscuro', () => {
    const servicio = crear();

    servicio.alternar();
    TestBed.tick();
    servicio.alternar();
    TestBed.tick();

    expect(servicio.tema()).toBe('oscuro');
    expect(document.documentElement.dataset['tema']).toBeUndefined();
    expect(meta.content).toBe('#0b1020');
    expect(localStorage.getItem(CLAVE_TEMA)).toBe('oscuro');
  });

  it('arranca con el tema guardado', () => {
    localStorage.setItem(CLAVE_TEMA, 'claro');

    const servicio = crear();

    expect(servicio.tema()).toBe('claro');
    expect(document.documentElement.dataset['tema']).toBe('claro');
  });
});
