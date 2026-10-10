import { HttpErrorResponse } from '@angular/common/http';
import { Signal, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { vi } from 'vitest';

import { UsuariosService } from '../../core/api/usuarios.service';
import { Pagina } from '../../core/modelos/pagina';
import { EstadoCuenta, Rol } from '../../core/modelos/usuario';
import { FiltrosDeUsuarios, UsuarioResumen } from '../../core/modelos/usuario-resumen';
import { Sesion } from '../../core/modelos/sesion';
import { SesionService } from '../../core/sesion/sesion.service';
import { UsuariosPage } from './usuarios.page';

// Como responde GET /api/usuarios (UsuariosControllerTest)
const ANA: UsuarioResumen = {
  idUsuario: 7,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-12T11:00:00',
};

const ADMIN: UsuarioResumen = {
  idUsuario: 1,
  nombre: 'Administrador',
  apellido: 'Inicial',
  correo: 'admin@tienda.bo',
  rol: 'ADMINISTRADOR',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-10T08:00:00',
};

function pagina(contenido: UsuarioResumen[], cambios: Partial<Pagina<UsuarioResumen>> = {}) {
  return { contenido, pagina: 0, tamano: 20, totalElementos: 21, totalPaginas: 2, ...cambios };
}

const SESION_ADMIN: Sesion = {
  token: 'jwt.admin',
  tipo: 'Bearer',
  expiracion: new Date(Date.now() + 3_600_000).toISOString(),
  usuario: { ...ADMIN, telefono: null },
};

// Lo que la pantalla usa del httpResource
class RecursoFalso {
  readonly value = signal<Pagina<UsuarioResumen> | undefined>(pagina([ANA, ADMIN]));
  readonly isLoading = signal(false);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class UsuariosFalso {
  recurso = new RecursoFalso();
  filtros: Signal<FiltrosDeUsuarios> | null = null;
  respuesta: Promise<UsuarioResumen> = Promise.resolve(ANA);
  cambiosDeRol: Array<{ id: number; rol: Rol }> = [];
  cambiosDeEstado: Array<{ id: number; estado: EstadoCuenta }> = [];

  listado(filtros: Signal<FiltrosDeUsuarios>) {
    this.filtros = filtros;
    return this.recurso;
  }

  cambiarRol(id: number, rol: Rol): Promise<UsuarioResumen> {
    this.cambiosDeRol.push({ id, rol });
    return this.respuesta;
  }

  cambiarEstado(id: number, estado: EstadoCuenta): Promise<UsuarioResumen> {
    this.cambiosDeEstado.push({ id, estado });
    return this.respuesta;
  }
}

describe('UsuariosPage', () => {
  let harness: RouterTestingHarness;
  let usuarios: UsuariosFalso;

  async function abrir(url = '/usuarios', configurar: (falso: UsuariosFalso) => void = () => {}) {
    TestBed.resetTestingModule();
    localStorage.clear();
    usuarios = new UsuariosFalso();
    configurar(usuarios);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'usuarios', component: UsuariosPage }]),
        { provide: UsuariosService, useValue: usuarios },
      ],
    });
    TestBed.inject(SesionService).iniciar(SESION_ADMIN);
    harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url, UsuariosPage);
    harness.fixture.autoDetectChanges();
    await harness.fixture.whenStable();
  }

  afterEach(() => localStorage.clear());

  function pantalla(): HTMLElement {
    return harness.routeNativeElement as HTMLElement;
  }

  function texto(): string {
    return pantalla().textContent ?? '';
  }

  function url(): string {
    return TestBed.inject(Router).url;
  }

  function boton(texto: string): HTMLButtonElement {
    const botones: HTMLButtonElement[] = Array.from(pantalla().querySelectorAll('button'));
    const encontrado = botones.find((cada) => cada.textContent?.trim() === texto);
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${texto}»`);
    }
    return encontrado;
  }

  async function elegir(id: string, valor: string) {
    const selector: HTMLSelectElement = pantalla().querySelector('#' + id)!;
    selector.value = valor;
    selector.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  // HU-05 RF-2: nombre, correo, rol, estado y fecha de alta
  it('muestra los usuarios de la página', async () => {
    await abrir();

    expect(texto()).toContain('Ana Rojas');
    expect(texto()).toContain('ana@mail.com');
    expect(texto()).toContain('Cliente');
    expect(texto()).toContain('Activa');
    expect(texto()).toContain('12/09/2026');
    expect(texto()).toContain('Administrador Inicial');
  });

  // HU-05 RF-2: totales de usuarios y de páginas
  it('informa la página y el total', async () => {
    await abrir();

    expect(texto()).toContain('Página 1 de 2');
    expect(texto()).toContain('21 usuarios');
  });

  // Recargar o compartir el enlace conserva la vista
  // HU-05 RF-3
  it('toma los filtros y la página de la URL', async () => {
    await abrir('/usuarios?rol=CLIENTE&estado=ACTIVA&texto=ana&pagina=1');

    expect(usuarios.filtros?.()).toEqual({ rol: 'CLIENTE', estado: 'ACTIVA', texto: 'ana', pagina: 1 });
  });

  // Un enlace alterado no llega al backend como un 400
  // HU-05 RF-3
  it('descarta valores de la URL que no son válidos', async () => {
    await abrir('/usuarios?rol=JEFE&estado=BORRADA&pagina=-3');

    expect(usuarios.filtros?.()).toEqual({ rol: null, estado: null, texto: '', pagina: 0 });
  });

  // HU-05 RF-3
  it('filtrar por rol lo lleva a la URL y vuelve a la primera página', async () => {
    await abrir('/usuarios?pagina=1');

    await elegir('filtro-rol', 'EMPLEADO');

    expect(url()).toBe('/usuarios?rol=EMPLEADO');
    expect(usuarios.filtros?.().rol).toBe('EMPLEADO');
  });

  // HU-05 RF-3
  it('filtrar por estado lo lleva a la URL', async () => {
    await abrir();

    await elegir('filtro-estado', 'DESACTIVADA');

    expect(url()).toBe('/usuarios?estado=DESACTIVADA');
  });

  // HU-05 RF-3
  it('volver a «Todos» quita el filtro', async () => {
    await abrir('/usuarios?rol=CLIENTE');

    await elegir('filtro-rol', '');

    expect(url()).toBe('/usuarios');
  });

  // La búsqueda espera a que se deje de escribir para no pedir una lista por letra
  // HU-05 RF-3
  it('la búsqueda por texto se aplica al dejar de escribir', async () => {
    await abrir();
    const busqueda: HTMLInputElement = pantalla().querySelector('#filtro-texto')!;

    busqueda.value = 'ana';
    busqueda.dispatchEvent(new Event('input'));
    await harness.fixture.whenStable();
    expect(url()).toBe('/usuarios');

    await new Promise((seguir) => setTimeout(seguir, 350));
    await harness.fixture.whenStable();
    expect(url()).toBe('/usuarios?texto=ana');
  });

  it('la búsqueda muestra el texto que ya venía en la URL', async () => {
    await abrir('/usuarios?texto=ana');

    const busqueda: HTMLInputElement = pantalla().querySelector('#filtro-texto')!;
    expect(busqueda.value).toBe('ana');
  });

  it('los filtros muestran lo que ya venía en la URL', async () => {
    await abrir('/usuarios?rol=EMPLEADO&estado=DESACTIVADA');

    expect((pantalla().querySelector('#filtro-rol') as HTMLSelectElement).value).toBe('EMPLEADO');
    expect((pantalla().querySelector('#filtro-estado') as HTMLSelectElement).value).toBe('DESACTIVADA');
  });

  // HU-05 RF-2
  it('pasa a la página siguiente conservando los filtros', async () => {
    await abrir('/usuarios?rol=CLIENTE');

    boton('Siguiente').click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/usuarios?rol=CLIENTE&pagina=1');
  });

  it('en la primera página no se puede ir atrás, en la última no se puede avanzar', async () => {
    await abrir();
    expect(boton('Anterior').disabled).toBe(true);
    expect(boton('Siguiente').disabled).toBe(false);

    usuarios.recurso.value.set(pagina([ANA], { pagina: 1 }));
    await harness.fixture.whenStable();
    expect(boton('Anterior').disabled).toBe(false);
    expect(boton('Siguiente').disabled).toBe(true);
  });

  // Caso límite: filtros sin resultados no son un error
  // HU-05 RF-3
  it('sin resultados lo dice en vez de mostrar una tabla vacía', async () => {
    await abrir('/usuarios?texto=nadie', (falso) =>
      falso.recurso.value.set(pagina([], { totalElementos: 0, totalPaginas: 0 })),
    );

    expect(pantalla().querySelector('table')).toBeNull();
    expect(texto()).toContain('No hay usuarios con esos filtros.');
  });

  // HU-05 RF-2
  it('mientras carga lo avisa', async () => {
    await abrir('/usuarios', (falso) => {
      falso.recurso.value.set(undefined);
      falso.recurso.isLoading.set(true);
    });

    expect(texto()).toContain('Cargando usuarios…');
  });

  // HU-05 RF-1, RF-12: si otro administrador le quitó el rol, el backend responde 403
  it('explica un acceso denegado con el diccionario', async () => {
    await abrir('/usuarios', (falso) => {
      falso.recurso.value.set(undefined);
      falso.recurso.error.set(
        new HttpErrorResponse({
          status: 403,
          error: { status: 403, detail: 'detalle técnico', codigo: 'ACCESO_DENEGADO' },
        }),
      );
    });

    expect(texto()).toContain('Tu cuenta no tiene acceso a esta parte del sistema.');
    expect(texto()).not.toContain('detalle técnico');
  });

  // HU-05 RF-2
  it('si la lista no carga deja reintentar', async () => {
    await abrir('/usuarios', (falso) => {
      falso.recurso.value.set(undefined);
      falso.recurso.error.set(new HttpErrorResponse({ status: 0 }));
    });

    boton('Reintentar').click();

    expect(usuarios.recurso.reload).toHaveBeenCalledTimes(1);
  });

  // --- Cambiar el rol y el estado ---

  function fila(correo: string): HTMLTableRowElement {
    const filas: HTMLTableRowElement[] = Array.from(pantalla().querySelectorAll('tbody tr'));
    const encontrada = filas.find((cada) => cada.textContent?.includes(correo));
    if (encontrada === undefined) {
      throw new Error(`No hay fila de ${correo}`);
    }
    return encontrada;
  }

  function dialogo(): HTMLDialogElement {
    return pantalla().querySelector('dialog')!;
  }

  // La respuesta del diálogo y la del backend se encadenan como promesas que
  // terminan un turno después de que whenStable() resuelve: se espera un macrotask antes de mirar la pantalla
  async function asentar() {
    await new Promise((seguir) => setTimeout(seguir, 0));
    await harness.fixture.whenStable();
  }

  async function responderDialogo(confirmar: boolean) {
    const clase = confirmar ? 'principal' : 'secundario';
    (dialogo().querySelector(`button.${clase}`) as HTMLButtonElement).click();
    await asentar();
  }

  async function elegirRol(correo: string, rol: Rol) {
    const selector = fila(correo).querySelector('select') as HTMLSelectElement;
    selector.value = rol;
    selector.dispatchEvent(new Event('change'));
    await harness.fixture.whenStable();
  }

  function botonDeFila(correo: string): HTMLButtonElement {
    return fila(correo).querySelector('button') as HTMLButtonElement;
  }

  // HU-05 RF-7, RF-8: sobre la propia cuenta no hay nada que ofrecer
  it('la fila propia no ofrece acciones', async () => {
    await abrir();

    const propia = fila('admin@tienda.bo');
    expect(propia.textContent).toContain('Tú');
    expect(propia.querySelector('select')).toBeNull();
    expect(propia.querySelector('button')).toBeNull();
  });

  // Cada control dice de quién es: en una tabla, «Tipo de cuenta» solo no alcanza
  // HU-05 RF-13: los cuatro roles fijos, ni uno más
  it('el selector de cada fila ofrece los cuatro roles fijos', async () => {
    await abrir();

    const selector = fila('ana@mail.com').querySelector('select') as HTMLSelectElement;
    expect(Array.from(selector.options).map((opcion) => opcion.textContent?.trim())).toEqual([
      'Cliente',
      'Administrador',
      'Empleado',
      'Contador',
    ]);
  });

  // HU-05 RF-4, RF-5
  it('los controles nombran a la persona de la fila', async () => {
    await abrir();

    const selector = fila('ana@mail.com').querySelector('select') as HTMLSelectElement;
    expect(selector.getAttribute('aria-label')).toBe('Tipo de cuenta de Ana Rojas');
    expect(botonDeFila('ana@mail.com').getAttribute('aria-label')).toBe('Desactivar a Ana Rojas');
  });

  // HU-05 RF-4, con confirmación
  it('cambiar el rol pide confirmación nombrando a la persona', async () => {
    await abrir();

    await elegirRol('ana@mail.com', 'EMPLEADO');

    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(dialogo().textContent).toContain('Ana Rojas');
    expect(dialogo().textContent).toContain('Empleado');
    expect(usuarios.cambiosDeRol).toEqual([]);
  });

  it('al confirmar cambia el rol y actualiza la fila', async () => {
    await abrir();
    usuarios.respuesta = Promise.resolve({ ...ANA, rol: 'EMPLEADO' });
    await elegirRol('ana@mail.com', 'EMPLEADO');

    await responderDialogo(true);

    expect(usuarios.cambiosDeRol).toEqual([{ id: 7, rol: 'EMPLEADO' }]);
    expect(fila('ana@mail.com').textContent).toContain('Empleado');
    expect(texto()).toContain('Ana Rojas ahora es Empleado.');
  });

  it('al cancelar no cambia nada y el selector vuelve al rol actual', async () => {
    await abrir();
    await elegirRol('ana@mail.com', 'EMPLEADO');

    await responderDialogo(false);

    expect(usuarios.cambiosDeRol).toEqual([]);
    expect((fila('ana@mail.com').querySelector('select') as HTMLSelectElement).value).toBe('CLIENTE');
  });

  // HU-05 RF-5, con confirmación
  it('desactivar pide confirmación y al confirmar desactiva', async () => {
    await abrir();
    usuarios.respuesta = Promise.resolve({ ...ANA, estado: 'DESACTIVADA' });

    botonDeFila('ana@mail.com').click();
    await harness.fixture.whenStable();
    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(dialogo().textContent).toContain('no podrá iniciar sesión');

    await responderDialogo(true);

    expect(usuarios.cambiosDeEstado).toEqual([{ id: 7, estado: 'DESACTIVADA' }]);
    expect(fila('ana@mail.com').textContent).toContain('Desactivada');
    expect(texto()).toContain('La cuenta de Ana Rojas quedó desactivada.');
  });

  it('cancelar la desactivación no llama al backend', async () => {
    await abrir();

    botonDeFila('ana@mail.com').click();
    await harness.fixture.whenStable();
    await responderDialogo(false);

    expect(usuarios.cambiosDeEstado).toEqual([]);
    expect(fila('ana@mail.com').textContent).toContain('Activa');
  });

  // HU-05 RF-6: reactivar solo devuelve el acceso, no pregunta
  it('reactivar no pide confirmación', async () => {
    const desactivada: UsuarioResumen = { ...ANA, estado: 'DESACTIVADA' };
    await abrir('/usuarios', (falso) => falso.recurso.value.set(pagina([desactivada, ADMIN])));
    usuarios.respuesta = Promise.resolve(ANA);

    expect(botonDeFila('ana@mail.com').textContent).toContain('Reactivar');
    botonDeFila('ana@mail.com').click();
    await asentar();

    expect(dialogo().hasAttribute('open')).toBe(false);
    expect(usuarios.cambiosDeEstado).toEqual([{ id: 7, estado: 'ACTIVA' }]);
    expect(texto()).toContain('La cuenta de Ana Rojas quedó reactivada.');
  });

  // HU-05 RF-10: primero hay que reactivarla
  it('en una cuenta desactivada no se puede cambiar el rol', async () => {
    const desactivada: UsuarioResumen = { ...ANA, estado: 'DESACTIVADA' };
    await abrir('/usuarios', (falso) => falso.recurso.value.set(pagina([desactivada, ADMIN])));

    const selector = fila('ana@mail.com').querySelector('select') as HTMLSelectElement;
    expect(selector.disabled).toBe(true);
    const ayuda = document.getElementById(selector.getAttribute('aria-describedby') ?? '');
    expect(ayuda?.textContent).toContain('Reactívala');
  });

  // HU-05 RF-12: el motivo del rechazo, y la fila como estaba
  it('si el backend rechaza el cambio avisa el motivo y no toca la fila', async () => {
    await abrir('/usuarios', (falso) => {
      falso.respuesta = Promise.reject({
        status: 409,
        detail: 'detalle técnico',
        codigo: 'OPERACION_SIMULTANEA',
      });
      falso.respuesta.catch(() => undefined);
      falso.recurso.value.set(pagina([{ ...ANA, rol: 'EMPLEADO' }, ADMIN]));
    });
    await elegirRol('ana@mail.com', 'CLIENTE');

    await responderDialogo(true);

    expect(texto()).toContain('Otra persona está modificando');
    expect(texto()).not.toContain('detalle técnico');
    expect(fila('ana@mail.com').textContent).toContain('Empleado');
    expect((fila('ana@mail.com').querySelector('select') as HTMLSelectElement).value).toBe('EMPLEADO');
  });

  // Un segundo clic mientras se guarda no puede mandar otra petición
  // HU-05 RF-12
  it('mientras se guarda, los controles de la fila quedan deshabilitados', async () => {
    let terminar: (usuario: UsuarioResumen) => void = () => {};
    await abrir('/usuarios', (falso) => {
      falso.respuesta = new Promise((resolver) => (terminar = resolver));
      falso.recurso.value.set(pagina([{ ...ANA, estado: 'DESACTIVADA' }, ADMIN]));
    });

    botonDeFila('ana@mail.com').click();
    await harness.fixture.whenStable();

    expect(botonDeFila('ana@mail.com').disabled).toBe(true);
    terminar(ANA);
    await asentar();
    expect(botonDeFila('ana@mail.com').disabled).toBe(false);
  });

  // --- Hallazgos de la revisión de código ---

  // La URL guarda el texto recortado: al llegar no puede pisar lo escrito
  it('la búsqueda no le quita el espacio final a lo que se está escribiendo', async () => {
    await abrir();
    const busqueda: HTMLInputElement = pantalla().querySelector('#filtro-texto')!;

    busqueda.value = 'ana ';
    busqueda.dispatchEvent(new Event('input'));
    await new Promise((seguir) => setTimeout(seguir, 350));
    await harness.fixture.whenStable();

    expect(url()).toBe('/usuarios?texto=ana');
    expect(busqueda.value).toBe('ana ');
  });

  // Volver atrás en el navegador sí trae el texto de esa URL
  it('una URL con otro texto actualiza la búsqueda', async () => {
    await abrir('/usuarios?texto=ana');

    await harness.navigateByUrl('/usuarios?texto=luis');
    await harness.fixture.whenStable();

    const busqueda: HTMLInputElement = pantalla().querySelector('#filtro-texto')!;
    expect(busqueda.value).toBe('luis');
  });

  // Dos operaciones a la vez: que termine una no habilita la otra
  // HU-05 RF-12
  it('cada fila queda deshabilitada hasta que termina su propia operación', async () => {
    const LUIS: UsuarioResumen = { ...ANA, idUsuario: 8, nombre: 'Luis', correo: 'luis@mail.com', estado: 'DESACTIVADA' };
    const terminar: Array<(usuario: UsuarioResumen) => void> = [];
    await abrir('/usuarios', (falso) =>
      falso.recurso.value.set(pagina([{ ...ANA, estado: 'DESACTIVADA' }, LUIS, ADMIN])),
    );
    usuarios.cambiarEstado = (id, estado) => {
      usuarios.cambiosDeEstado.push({ id, estado });
      return new Promise((resolver) => terminar.push(resolver));
    };

    botonDeFila('ana@mail.com').click();
    botonDeFila('luis@mail.com').click();
    await asentar();
    terminar[0](ANA);
    await asentar();

    expect(botonDeFila('ana@mail.com').disabled).toBe(false);
    expect(botonDeFila('luis@mail.com').disabled).toBe(true);
  });

  // Un número que el backend no puede leer como página sería un error de carga
  // HU-05 RF-2
  it('descarta una página demasiado grande', async () => {
    await abrir('/usuarios?pagina=1e20');

    expect(usuarios.filtros?.().pagina).toBe(0);
  });

  // Una página que ya no existe ofrece volver al principio, no retroceder de a una
  // HU-05 RF-2
  it('fuera de rango ofrece volver a la primera página', async () => {
    await abrir('/usuarios?rol=CLIENTE&pagina=50', (falso) =>
      falso.recurso.value.set(pagina([], { pagina: 50, totalElementos: 21, totalPaginas: 2 })),
    );

    expect(texto()).toContain('Esta página no existe.');
    boton('Ir a la primera página').click();
    await harness.fixture.whenStable();

    expect(url()).toBe('/usuarios?rol=CLIENTE');
  });
});
