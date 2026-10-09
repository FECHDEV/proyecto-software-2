import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { CuentaService } from '../../core/api/cuenta.service';
import { CambioDeContrasena, DatosPersonales } from '../../core/modelos/cuenta';
import { ErrorApi } from '../../core/modelos/error-api';
import { Sesion } from '../../core/modelos/sesion';
import { Usuario } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { MisDatosPage } from './mis-datos.page';

// Como responde GET /api/cuenta/datos-personales (CuentaControllerTest)
const ANA: Usuario = {
  idUsuario: 2,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  telefono: '70000000',
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-14T10:00:00',
};

function sesionDe(usuario: Usuario, token = 'jwt.de.prueba'): Sesion {
  return {
    token,
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
    usuario,
  };
}

// Lo que la pantalla usa del httpResource
class RecursoFalso {
  readonly value = signal<Usuario | undefined>(ANA);
  readonly isLoading = signal(false);
  readonly error = signal<unknown>(undefined);
  readonly reload = vi.fn(() => true);
  hasValue(): boolean {
    return this.value() !== undefined;
  }
}

class CuentaFalsa {
  recurso = new RecursoFalso();
  respuestaDatos: Promise<Usuario> = Promise.resolve(ANA);
  llamadasDatos: DatosPersonales[] = [];
  respuestaContrasena: Promise<Sesion> = Promise.resolve(sesionDe(ANA, 'jwt.nuevo'));
  llamadasContrasena: CambioDeContrasena[] = [];

  datosPersonales() {
    return this.recurso;
  }

  actualizarDatos(datos: DatosPersonales): Promise<Usuario> {
    this.llamadasDatos.push(datos);
    return this.respuestaDatos;
  }

  cambiarContrasena(cambio: CambioDeContrasena): Promise<Sesion> {
    this.llamadasContrasena.push(cambio);
    return this.respuestaContrasena;
  }
}

function rechaza<T>(error: ErrorApi): Promise<T> {
  return Promise.reject(error);
}

describe('MisDatosPage', () => {
  let fijo: ComponentFixture<MisDatosPage>;
  let cuenta: CuentaFalsa;
  let sesion: SesionService;

  async function preparar(configurar: (falsa: CuentaFalsa) => void = () => {}) {
    TestBed.resetTestingModule();
    localStorage.clear();
    cuenta = new CuentaFalsa();
    configurar(cuenta);

    await TestBed.configureTestingModule({
      imports: [MisDatosPage],
      providers: [provideRouter([]), { provide: CuentaService, useValue: cuenta }],
    }).compileComponents();

    sesion = TestBed.inject(SesionService);
    sesion.iniciar(sesionDe(ANA));

    fijo = TestBed.createComponent(MisDatosPage);
    fijo.detectChanges();
  }

  beforeEach(async () => {
    await preparar();
  });

  afterEach(() => localStorage.clear());

  function campo(id: string): HTMLInputElement {
    return fijo.nativeElement.querySelector('#' + id);
  }

  function escribir(id: string, valor: string) {
    const entrada = campo(id);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fijo.detectChanges();
  }

  async function enviar(idFormulario: string) {
    fijo.nativeElement.querySelector('#' + idFormulario).dispatchEvent(new Event('submit'));
    await fijo.whenStable();
    fijo.detectChanges();
  }

  function texto(): string {
    return fijo.nativeElement.textContent as string;
  }

  // HU-04 RF-1: los datos de la cuenta, sin la contraseña
  it('muestra los datos de la cuenta', () => {
    expect(campo('nombre').value).toBe('Ana');
    expect(campo('apellido').value).toBe('Rojas');
    expect(campo('telefono').value).toBe('70000000');
    expect(texto()).toContain('ana@mail.com');
    expect(texto()).toContain('Cliente');
    expect(texto()).toContain('Activa');
    expect(texto()).toContain('14/09/2026');
  });

  // HU-04 RF-4: correo, rol y estado no se ofrecen para editar
  it('solo deja editar nombre, apellido y teléfono', () => {
    const editables = Array.from(
      fijo.nativeElement.querySelectorAll('#formulario-datos input') as NodeListOf<HTMLInputElement>,
    ).map((entrada) => entrada.id);

    expect(editables).toEqual(['nombre', 'apellido', 'telefono']);
  });

  // HU-04 RF-1: el mismo caso para todos los roles
  it('nombra al administrador', async () => {
    await preparar((falsa) =>
      falsa.recurso.value.set({ ...ANA, rol: 'ADMINISTRADOR' }),
    );

    expect(texto()).toContain('Administrador');
  });

  it('nombra al empleado', async () => {
    await preparar((falsa) =>
      falsa.recurso.value.set({ ...ANA, rol: 'EMPLEADO' }),
    );

    expect(texto()).toContain('Empleado');
  });

  // HU-04 RF-10
  it('mientras carga no muestra el formulario', async () => {
    await preparar((falsa) => {
      falsa.recurso.value.set(undefined);
      falsa.recurso.isLoading.set(true);
    });

    expect(fijo.nativeElement.querySelector('form')).toBeNull();
    expect(texto()).toContain('Cargando tus datos');
  });

  // HU-04 RF-10
  it('si no se pudieron cargar, avisa y deja reintentar', async () => {
    await preparar((falsa) => {
      falsa.recurso.value.set(undefined);
      falsa.recurso.error.set(new Error('sin red'));
    });

    expect(fijo.nativeElement.querySelector('form')).toBeNull();
    expect(texto()).toContain('No pudimos cargar tus datos');
    const boton: HTMLButtonElement = fijo.nativeElement.querySelector('button.reintentar');
    boton.click();
    expect(cuenta.recurso.reload).toHaveBeenCalledTimes(1);
  });

  // HU-04 RF-2
  it('guarda los datos y confirma', async () => {
    const guardada: Usuario = { ...ANA, nombre: 'Ana María', telefono: '71111111' };
    cuenta.respuestaDatos = Promise.resolve(guardada);
    escribir('nombre', 'Ana María');
    escribir('telefono', '71111111');

    await enviar('formulario-datos');

    expect(cuenta.llamadasDatos).toEqual([
      { nombre: 'Ana María', apellido: 'Rojas', telefono: '71111111' },
    ]);
    expect(texto()).toContain('Datos guardados.');
    expect(campo('nombre').value).toBe('Ana María');
  });

  // El encabezado lee el nombre de la sesión: tiene que cambiar sin volver a entrar
  // HU-04 RF-2
  it('actualiza el nombre de la sesión', async () => {
    cuenta.respuestaDatos = Promise.resolve({ ...ANA, nombre: 'Ana María' });
    escribir('nombre', 'Ana María');

    await enviar('formulario-datos');

    expect(sesion.usuario()?.nombre).toBe('Ana María');
    expect(sesion.token()).toBe('jwt.de.prueba');
  });

  // HU-04 RF-2
  it('el aviso de guardado se va al volver a editar', async () => {
    cuenta.respuestaDatos = Promise.resolve({ ...ANA, nombre: 'Ana María' });
    escribir('nombre', 'Ana María');
    await enviar('formulario-datos');

    escribir('apellido', 'Rojas Paz');

    expect(texto()).not.toContain('Datos guardados.');
  });

  // HU-04 RF-3: las mismas reglas que el registro
  it('sin nombre no llama a la API', async () => {
    escribir('nombre', '');

    await enviar('formulario-datos');

    expect(cuenta.llamadasDatos).toEqual([]);
    expect(texto()).toContain('Escribe tu nombre.');
  });

  it('un nombre de solo espacios cuenta como vacío', async () => {
    escribir('nombre', '   ');

    await enviar('formulario-datos');

    expect(cuenta.llamadasDatos).toEqual([]);
    expect(texto()).toContain('Escribe tu nombre.');
  });

  it('frena un apellido de más de 80 caracteres', async () => {
    escribir('apellido', 'a'.repeat(81));

    await enviar('formulario-datos');

    expect(cuenta.llamadasDatos).toEqual([]);
    expect(texto()).toContain('El apellido admite hasta 80 caracteres.');
  });

  it('frena un teléfono de más de 20 caracteres', async () => {
    escribir('telefono', '7'.repeat(21));

    await enviar('formulario-datos');

    expect(cuenta.llamadasDatos).toEqual([]);
    expect(texto()).toContain('El teléfono admite hasta 20 caracteres.');
  });

  // HU-04 RF-3: el teléfono es opcional
  it('acepta dejar el teléfono vacío', async () => {
    cuenta.respuestaDatos = Promise.resolve({ ...ANA, telefono: null });
    escribir('telefono', '');

    await enviar('formulario-datos');

    expect(cuenta.llamadasDatos).toEqual([{ nombre: 'Ana', apellido: 'Rojas', telefono: '' }]);
    expect(texto()).toContain('Datos guardados.');
  });

  // HU-04 RF-3
  it('reparte los errores del backend bajo cada campo', async () => {
    cuenta.respuestaDatos = rechaza({
      status: 400,
      detail: 'detalle técnico del backend',
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'apellido', mensaje: 'admite hasta 80 caracteres' }],
    });
    escribir('apellido', 'Rojas Paz');

    await enviar('formulario-datos');

    expect(fijo.nativeElement.querySelector('#error-apellido').textContent).toContain(
      'El apellido admite hasta 80 caracteres.',
    );
    expect(texto()).not.toContain('detalle técnico');
  });

  it('avisa cuando no se pudo contactar al servidor', async () => {
    cuenta.respuestaDatos = rechaza({ status: 0, detail: 'x', codigo: 'SIN_CONEXION' });
    escribir('nombre', 'Ana María');

    await enviar('formulario-datos');

    expect(texto()).toContain('No pudimos conectar con el servidor.');
    expect(texto()).not.toContain('Datos guardados.');
  });

  // --- Cambiar la contraseña ---

  function completarContrasenas(actual: string, nueva: string) {
    escribir('contrasenaActual', actual);
    escribir('contrasenaNueva', nueva);
  }

  // HU-04 RF-12: cada botón muestra solo su campo
  it('la contraseña actual y la nueva tienen su botón para verlas', () => {
    const botones: HTMLButtonElement[] = Array.from(fijo.nativeElement.querySelectorAll('button.ver-contrasena'));
    const actual: HTMLInputElement = fijo.nativeElement.querySelector('#contrasenaActual');
    const nueva: HTMLInputElement = fijo.nativeElement.querySelector('#contrasenaNueva');

    expect(botones.length).toBe(2);
    botones[1].click();
    fijo.detectChanges();

    expect(nueva.type).toBe('text');
    expect(actual.type).toBe('password');
  });

  it('pide la contraseña actual y la nueva', () => {
    expect(campo('contrasenaActual').type).toBe('password');
    expect(campo('contrasenaActual').getAttribute('autocomplete')).toBe('current-password');
    expect(campo('contrasenaNueva').type).toBe('password');
    expect(campo('contrasenaNueva').getAttribute('autocomplete')).toBe('new-password');
  });

  // HU-04 RF-5, RF-6: el token anterior deja de valer, así que la sesión pasa al nuevo
  it('cambia la contraseña y sigue con el token nuevo', async () => {
    completarContrasenas('actual-123', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(cuenta.llamadasContrasena).toEqual([
      { contrasenaActual: 'actual-123', contrasenaNueva: 'nueva-clave-1' },
    ]);
    expect(sesion.token()).toBe('jwt.nuevo');
    expect(texto()).toContain('Contraseña actualizada.');
  });

  // HU-04 RF-5, RF-12: la próxima contraseña que se escriba no queda a la vista
  it('después del cambio vuelve a ocultar las contraseñas que se mostraron', async () => {
    const botones: HTMLButtonElement[] = Array.from(fijo.nativeElement.querySelectorAll('button.ver-contrasena'));
    botones.forEach((boton) => boton.click());
    completarContrasenas('actual-123', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(campo('contrasenaActual').type).toBe('password');
    expect(campo('contrasenaNueva').type).toBe('password');
    expect(botones.map((boton) => boton.getAttribute('aria-label'))).toEqual([
      'Mostrar contraseña',
      'Mostrar contraseña',
    ]);
  });

  // Las contraseñas no quedan escritas en pantalla ni marcadas como error
  // HU-04 RF-5
  it('después del cambio vacía los campos sin marcar errores', async () => {
    completarContrasenas('actual-123', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(campo('contrasenaActual').value).toBe('');
    expect(campo('contrasenaNueva').value).toBe('');
    expect(fijo.nativeElement.querySelector('#error-contrasenaActual')).toBeNull();
    expect(fijo.nativeElement.querySelector('#error-contrasenaNueva')).toBeNull();
  });

  it('la confirmación se va al empezar otro cambio', async () => {
    completarContrasenas('actual-123', 'nueva-clave-1');
    await enviar('formulario-contrasena');

    escribir('contrasenaActual', 'n');

    expect(texto()).not.toContain('Contraseña actualizada.');
  });

  // HU-04 RF-8
  it('sin contraseña actual no llama a la API', async () => {
    completarContrasenas('', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(cuenta.llamadasContrasena).toEqual([]);
    expect(texto()).toContain('Escribe tu contraseña actual.');
  });

  // HU-04 RF-8
  it('frena una contraseña nueva de 7 caracteres', async () => {
    completarContrasenas('actual-123', 'siete12');

    await enviar('formulario-contrasena');

    expect(cuenta.llamadasContrasena).toEqual([]);
    expect(texto()).toContain('La contraseña nueva necesita al menos 8 caracteres.');
  });

  // HU-04 RF-8
  it('frena una contraseña nueva de más de 72 bytes', async () => {
    completarContrasenas('actual-123', 'ñ'.repeat(37));

    await enviar('formulario-contrasena');

    expect(cuenta.llamadasContrasena).toEqual([]);
    expect(texto()).toContain('demasiado larga');
  });

  // HU-04 RF-8
  it('la ayuda del mínimo se va al cumplirlo', () => {
    escribir('contrasenaNueva', 'siete12');
    expect(texto()).toContain('Al menos 8 caracteres.');

    escribir('contrasenaNueva', 'ocho1234');
    expect(texto()).not.toContain('Al menos 8 caracteres.');
  });

  // HU-04 RF-7
  it('avisa que la contraseña actual no es correcta', async () => {
    cuenta.respuestaContrasena = rechaza({
      status: 400,
      detail: 'detalle técnico del backend',
      codigo: 'CONTRASENA_ACTUAL_INCORRECTA',
    });
    completarContrasenas('otra-clave', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(texto()).toContain('La contraseña actual no es correcta.');
    expect(texto()).not.toContain('detalle técnico');
    expect(texto()).not.toContain('Contraseña actualizada.');
    expect(sesion.token()).toBe('jwt.de.prueba');
  });

  // HU-04 RF-7
  it('avisa cuando hay que esperar por los intentos fallidos', async () => {
    cuenta.respuestaContrasena = rechaza({ status: 429, detail: 'x', codigo: 'INTENTOS_EXCEDIDOS' });
    completarContrasenas('otra-clave', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(texto()).toContain('Espera 15 minutos');
  });

  // HU-04 RF-8
  it('reparte los errores del backend bajo la contraseña nueva', async () => {
    cuenta.respuestaContrasena = rechaza({
      status: 400,
      detail: 'x',
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'contrasenaNueva', mensaje: 'admite hasta 72 bytes' }],
    });
    completarContrasenas('actual-123', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    expect(fijo.nativeElement.querySelector('#error-contrasenaNueva').textContent).toContain(
      'La contraseña nueva admite hasta 72 bytes.',
    );
  });

  // Un error en un formulario no se muestra en el otro
  it('los avisos de cada formulario quedan en el suyo', async () => {
    cuenta.respuestaContrasena = rechaza({
      status: 400,
      detail: 'x',
      codigo: 'CONTRASENA_ACTUAL_INCORRECTA',
    });
    completarContrasenas('otra-clave', 'nueva-clave-1');

    await enviar('formulario-contrasena');

    const seccionDeDatos: HTMLElement = fijo.nativeElement.querySelector('[aria-labelledby="titulo-datos"]');
    expect(seccionDeDatos.textContent).not.toContain('La contraseña actual no es correcta.');
  });

  it('los errores de campo no se anuncian como alerta', async () => {
    escribir('nombre', '');
    escribir('apellido', '');
    await enviar('formulario-datos');

    expect(fijo.nativeElement.querySelectorAll('.error').length).toBe(2);
    expect(fijo.nativeElement.querySelector('.error[role]')).toBeNull();
  });

  // --- Cambios sin guardar ---

  function conCambios(): boolean {
    return fijo.componentInstance.tieneCambiosSinGuardar();
  }

  // HU-04 RF-9
  it('recién cargada no tiene cambios sin guardar', () => {
    expect(conCambios()).toBe(false);
  });

  // HU-04 RF-9
  it('editar un dato deja cambios sin guardar', () => {
    escribir('telefono', '71111111');

    expect(conCambios()).toBe(true);
  });

  // HU-04 RF-9
  it('volver al valor guardado ya no cuenta como cambio', () => {
    escribir('nombre', 'Ana María');
    escribir('nombre', 'Ana');

    expect(conCambios()).toBe(false);
  });

  // HU-04 RF-9
  it('después de guardar no quedan cambios', async () => {
    cuenta.respuestaDatos = Promise.resolve({ ...ANA, nombre: 'Ana María' });
    escribir('nombre', 'Ana María');

    await enviar('formulario-datos');

    expect(conCambios()).toBe(false);
  });

  // HU-04 RF-9
  it('una contraseña a medio escribir cuenta como cambio', () => {
    escribir('contrasenaActual', 'actual-1');

    expect(conCambios()).toBe(true);
  });

  // Cerrar la pestaña o recargar también pierde lo escrito
  // HU-04 RF-9
  it('avisa al cerrar la pestaña con cambios sin guardar', () => {
    escribir('nombre', 'Ana María');
    const evento = new Event('beforeunload', { cancelable: true });

    window.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(true);
  });

  // HU-04 RF-9
  it('sin cambios deja cerrar la pestaña', () => {
    const evento = new Event('beforeunload', { cancelable: true });

    window.dispatchEvent(evento);

    expect(evento.defaultPrevented).toBe(false);
  });
});
