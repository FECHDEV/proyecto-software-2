import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ErrorApi } from '../modelos/error-api';
import { DatosDeRegistro } from '../modelos/registro';
import { Sesion } from '../modelos/sesion';
import { Usuario } from '../modelos/usuario';
import { AutenticacionService } from './autenticacion.service';

const RESPUESTA: Sesion = {
  token: 'jwt.de.prueba',
  tipo: 'Bearer',
  expiracion: '2026-09-18T03:00:00Z',
  usuario: {
    idUsuario: 2,
    nombre: 'Ana',
    apellido: 'Rojas',
    correo: 'ana@mail.com',
    telefono: null,
    rol: 'CLIENTE',
    estado: 'ACTIVA',
    fechaRegistro: '2026-09-14T10:00:00',
  },
};

describe('AutenticacionService', () => {
  let servicio: AutenticacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AutenticacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envía el correo y la contraseña, y devuelve la sesión', async () => {
    const promesa = servicio.iniciarSesion('ana@mail.com', 'secreta12');

    const peticion = http.expectOne('/api/auth/inicio-sesion');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ correo: 'ana@mail.com', contrasena: 'secreta12' });
    peticion.flush(RESPUESTA);

    await expect(promesa).resolves.toEqual(RESPUESTA);
  });

  // RF-3: la cuenta se busca con el correo en minúsculas y sin espacios
  it('normaliza el correo antes de enviarlo', async () => {
    const promesa = servicio.iniciarSesion('  Ana@Mail.COM  ', 'secreta12');

    const peticion = http.expectOne('/api/auth/inicio-sesion');
    expect(peticion.request.body.correo).toBe('ana@mail.com');
    peticion.flush(RESPUESTA);

    await promesa;
  });

  it('no toca la contraseña, que sí distingue mayúsculas', async () => {
    const promesa = servicio.iniciarSesion('ana@mail.com', '  Secreta12  ');

    const peticion = http.expectOne('/api/auth/inicio-sesion');
    expect(peticion.request.body.contrasena).toBe('  Secreta12  ');
    peticion.flush(RESPUESTA);

    await promesa;
  });

  // RF-11: el backend responde 401 con un ProblemDetail y su código de negocio
  it('rechaza con el error de la API cuando las credenciales no sirven', async () => {
    const promesa = servicio.iniciarSesion('ana@mail.com', 'incorrecta');

    http.expectOne('/api/auth/inicio-sesion').flush(
      {
        type: 'about:blank',
        title: 'Unauthorized',
        status: 401,
        detail: 'Correo o contraseña incorrectos.',
        codigo: 'CREDENCIALES_INCORRECTAS',
      },
      { status: 401, statusText: 'Unauthorized' },
    );

    await expect(promesa).rejects.toMatchObject({
      status: 401,
      codigo: 'CREDENCIALES_INCORRECTAS',
    } as ErrorApi);
  });

  // Regresión: con el contrato viejo ({codigo, mensaje}) todos los errores del
  // backend caían en SIN_CONEXION y el mensaje real nunca se mostraba
  it('un 429 del límite de intentos conserva su código', async () => {
    const promesa = servicio.iniciarSesion('ana@mail.com', 'incorrecta');

    http.expectOne('/api/auth/inicio-sesion').flush(
      {
        type: 'about:blank',
        title: 'Too Many Requests',
        status: 429,
        detail: 'Demasiados intentos fallidos. Intente nuevamente en unos minutos.',
        codigo: 'INTENTOS_EXCEDIDOS',
      },
      { status: 429, statusText: 'Too Many Requests' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'INTENTOS_EXCEDIDOS' });
  });

  it('rechaza con un error propio cuando el servidor no responde', async () => {
    const promesa = servicio.iniciarSesion('ana@mail.com', 'secreta12');

    http.expectOne('/api/auth/inicio-sesion').error(new ProgressEvent('error'));

    await expect(promesa).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});

// Los fixtures de abajo son copia de lo que responde el backend en
// AuthControllerTest y RegistroClienteIntegracionTest, no invención: el contrato
// inventado fue justo lo que escondió el error del inicio de sesión.
const CLIENTE_REGISTRADO: Usuario = {
  idUsuario: 2,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  telefono: null,
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-17T10:00:00',
};

// HU-01 RF-2: el registro responde con la sesión, igual que el inicio de sesión
const SESION_REGISTRADA: Sesion = {
  token: 'token.de.ana',
  tipo: 'Bearer',
  expiracion: '2026-09-17T18:00:00Z',
  usuario: CLIENTE_REGISTRADO,
};

const DATOS: DatosDeRegistro = {
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  contrasena: 'secreta12',
  telefono: '',
};

describe('AutenticacionService · registro', () => {
  let servicio: AutenticacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AutenticacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // HU-01 RF-1, RF-2
  it('envía los datos del registro y devuelve la sesión', async () => {
    const promesa = servicio.registrarCliente({ ...DATOS, telefono: '70011122' });

    const peticion = http.expectOne('/api/auth/registro');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      contrasena: 'secreta12',
      telefono: '70011122',
    });
    peticion.flush(SESION_REGISTRADA, { status: 201, statusText: 'Created' });

    await expect(promesa).resolves.toEqual(SESION_REGISTRADA);
  });

  // HU-01 RF-7: el correo se guarda en minúsculas y sin espacios alrededor
  it('normaliza el correo y recorta los espacios de los demás datos', async () => {
    const promesa = servicio.registrarCliente({
      ...DATOS,
      nombre: '  Ana  ',
      apellido: ' Rojas ',
      correo: '  Ana@Mail.COM ',
      telefono: '  70011122  ',
    });

    const peticion = http.expectOne('/api/auth/registro');
    expect(peticion.request.body).toMatchObject({
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      telefono: '70011122',
    });
    peticion.flush(SESION_REGISTRADA, { status: 201, statusText: 'Created' });

    await promesa;
  });

  // HU-01 RF-10: la cuenta se registra sin teléfono
  it('envía el teléfono como null cuando queda vacío', async () => {
    const promesa = servicio.registrarCliente({ ...DATOS, telefono: '   ' });

    const peticion = http.expectOne('/api/auth/registro');
    expect(peticion.request.body.telefono).toBeNull();
    peticion.flush(SESION_REGISTRADA, { status: 201, statusText: 'Created' });

    await promesa;
  });

  it('no toca la contraseña', async () => {
    const promesa = servicio.registrarCliente({ ...DATOS, contrasena: '  Secreta12  ' });

    const peticion = http.expectOne('/api/auth/registro');
    expect(peticion.request.body.contrasena).toBe('  Secreta12  ');
    peticion.flush(SESION_REGISTRADA, { status: 201, statusText: 'Created' });

    await promesa;
  });

  // RF-9: el backend indica todos los campos a corregir, en un arreglo
  it('conserva la lista de campos a corregir de un 400', async () => {
    const promesa = servicio.registrarCliente({ ...DATOS, correo: 'ana-sin-arroba' });

    http.expectOne('/api/auth/registro').flush(
      {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        detail: 'Hay campos a corregir.',
        codigo: 'DATOS_INVALIDOS',
        errores: [
          { campo: 'correo', mensaje: 'no tiene formato de correo' },
          { campo: 'contrasena', mensaje: 'debe tener al menos 8 caracteres' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await expect(promesa).rejects.toMatchObject({
      codigo: 'DATOS_INVALIDOS',
      errores: [
        { campo: 'correo', mensaje: 'no tiene formato de correo' },
        { campo: 'contrasena', mensaje: 'debe tener al menos 8 caracteres' },
      ],
    } as ErrorApi);
  });

  // RF-15 (FA-02): el correo ya pertenece a una cuenta
  it('rechaza con CUENTA_EXISTENTE cuando el correo ya está registrado', async () => {
    const promesa = servicio.registrarCliente(DATOS);

    http.expectOne('/api/auth/registro').flush(
      {
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'Ya existe una cuenta registrada con ese correo.',
        codigo: 'CUENTA_EXISTENTE',
      },
      { status: 409, statusText: 'Conflict' },
    );

    await expect(promesa).rejects.toMatchObject({ status: 409, codigo: 'CUENTA_EXISTENTE' });
  });

  it('rechaza con un error propio cuando el servidor no responde', async () => {
    const promesa = servicio.registrarCliente(DATOS);

    http.expectOne('/api/auth/registro').error(new ProgressEvent('error'));

    await expect(promesa).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});

// Respuestas reales de AuthController: 202 al solicitar, 200 al restablecer,
// las dos con un {mensaje} que el frontend no muestra tal cual
describe('AutenticacionService, recuperacion de contrasena', () => {
  let servicio: AutenticacionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AutenticacionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  // RF-2: la cuenta se busca con el correo normalizado
  it('solicita la recuperacion con el correo en minusculas y sin espacios', async () => {
    const promesa = servicio.solicitarRecuperacion('  Ana@Mail.COM  ');

    const peticion = http.expectOne('/api/auth/recuperacion');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ correo: 'ana@mail.com' });
    peticion.flush(
      { mensaje: 'Si el correo está registrado, le enviamos las instrucciones para recuperar la contraseña.' },
      { status: 202, statusText: 'Accepted' },
    );

    await expect(promesa).resolves.toBeUndefined();
  });

  // RF-8: cuarta solicitud seguida
  it('rechaza con SOLICITUDES_EXCEDIDAS cuando se pidio demasiadas veces', async () => {
    const promesa = servicio.solicitarRecuperacion('ana@mail.com');

    http.expectOne('/api/auth/recuperacion').flush(
      {
        type: 'about:blank',
        title: 'Too Many Requests',
        status: 429,
        detail: 'Demasiadas solicitudes de recuperación. Espere unos minutos antes de volver a intentar.',
        codigo: 'SOLICITUDES_EXCEDIDAS',
      },
      { status: 429, statusText: 'Too Many Requests' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'SOLICITUDES_EXCEDIDAS' });
  });

  // RF-10: token vigente y contrasena nueva
  it('restablece la contrasena con el token tal como vino', async () => {
    const promesa = servicio.restablecerContrasena('  token-con-espacios  ', 'nueva-clave-1');

    const peticion = http.expectOne('/api/auth/recuperacion/restablecimiento');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      token: 'token-con-espacios',
      contrasena: 'nueva-clave-1',
    });
    peticion.flush({ mensaje: 'La contraseña se actualizó. Ya puede iniciar sesión con la nueva contraseña.' });

    await expect(promesa).resolves.toBeUndefined();
  });

  // RF-12 (FA-02): token inexistente, vencido o ya usado, todos iguales
  it('rechaza con TOKEN_RECUPERACION_INVALIDO', async () => {
    const promesa = servicio.restablecerContrasena('token-usado', 'nueva-clave-1');

    http.expectOne('/api/auth/recuperacion/restablecimiento').flush(
      {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        detail: 'El enlace de recuperación no es válido o ya venció. Solicite la recuperación de la contraseña nuevamente.',
        codigo: 'TOKEN_RECUPERACION_INVALIDO',
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await expect(promesa).rejects.toMatchObject({ codigo: 'TOKEN_RECUPERACION_INVALIDO' });
  });

  // RF-11: la contrasena nueva se valida como la del registro
  it('conserva los campos a corregir de un 400', async () => {
    const promesa = servicio.restablecerContrasena('token-vigente', 'corta');

    http.expectOne('/api/auth/recuperacion/restablecimiento').flush(
      {
        type: 'about:blank',
        title: 'Bad Request',
        status: 400,
        detail: 'Hay campos a corregir.',
        codigo: 'DATOS_INVALIDOS',
        errores: [{ campo: 'contrasena', mensaje: 'debe tener al menos 8 caracteres' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );

    await expect(promesa).rejects.toMatchObject({
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'contrasena', mensaje: 'debe tener al menos 8 caracteres' }],
    });
  });

  it('rechaza con un error propio cuando el servidor no responde', async () => {
    const promesa = servicio.solicitarRecuperacion('ana@mail.com');

    http.expectOne('/api/auth/recuperacion').error(new ProgressEvent('error'));

    await expect(promesa).rejects.toMatchObject({ codigo: 'SIN_CONEXION' });
  });
});
