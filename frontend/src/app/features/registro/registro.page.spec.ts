import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { ErrorApi } from '../../core/modelos/error-api';
import { DatosDeRegistro } from '../../core/modelos/registro';
import { Sesion } from '../../core/modelos/sesion';
import { Usuario } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { RegistroPage } from './registro.page';

const CLIENTE: Usuario = {
  idUsuario: 2,
  nombre: 'Ana',
  apellido: 'Rojas',
  correo: 'ana@mail.com',
  telefono: null,
  rol: 'CLIENTE',
  estado: 'ACTIVA',
  fechaRegistro: '2026-09-17T10:00:00',
};

const SESION: Sesion = { token: 'token.de.ana', tipo: 'Bearer', expiracion: '2026-10-09T23:00:00Z', usuario: CLIENTE };

class AutenticacionFalsa {
  respuesta: Promise<Sesion> = Promise.resolve(SESION);
  llamadas: DatosDeRegistro[] = [];

  registrarCliente(datos: DatosDeRegistro): Promise<Sesion> {
    this.llamadas.push(datos);
    return this.respuesta;
  }
}

class SesionFalsa {
  iniciadas: Sesion[] = [];

  iniciar(sesion: Sesion): void {
    this.iniciadas.push(sesion);
  }
}

// Cuerpo como el que arma ManejadorErrores (ProblemDetail + código propio)
function rechaza(error: ErrorApi): Promise<Sesion> {
  return Promise.reject(error);
}

describe('RegistroPage', () => {
  let fijo: ComponentFixture<RegistroPage>;
  let autenticacion: AutenticacionFalsa;
  let sesion: SesionFalsa;
  let navegaciones: string[];

  // El destino llega del inicio de sesión: a dónde iba la persona antes de crear la cuenta
  async function preparar(destino: string | null = null) {
    TestBed.resetTestingModule();
    autenticacion = new AutenticacionFalsa();
    sesion = new SesionFalsa();
    navegaciones = [];

    await TestBed.configureTestingModule({
      imports: [RegistroPage],
      providers: [
        provideRouter([]),
        { provide: AutenticacionService, useValue: autenticacion },
        { provide: SesionService, useValue: sesion },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(destino === null ? {} : { destino }) } },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    router.navigateByUrl = (url) => {
      navegaciones.push(String(url));
      return Promise.resolve(true);
    };

    fijo = TestBed.createComponent(RegistroPage);
    fijo.detectChanges();
  }

  beforeEach(async () => {
    await preparar();
  });

  function campo(id: string): HTMLInputElement {
    return fijo.nativeElement.querySelector('#' + id);
  }

  function escribir(id: string, valor: string) {
    const entrada = campo(id);
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fijo.detectChanges();
  }

  function completar(cambios: Record<string, string> = {}) {
    const datos: Record<string, string> = {
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      contrasena: 'secreta12',
      telefono: '',
      ...cambios,
    };
    for (const [id, valor] of Object.entries(datos)) {
      escribir(id, valor);
    }
  }

  async function enviar() {
    fijo.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fijo.whenStable();
    fijo.detectChanges();
  }

  function texto(): string {
    return fijo.nativeElement.textContent as string;
  }

  function mensajeDe(id: string): string {
    return fijo.nativeElement.querySelector('#error-' + id)?.textContent ?? '';
  }

  function iniciaSesion(): string | null {
    const enlace = Array.from(fijo.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>).find(
      (cada) => cada.textContent?.trim() === 'Inicia sesión',
    );
    return enlace?.getAttribute('href') ?? null;
  }

  // HU-01 RF-2: quien iba a comprar crea la cuenta y vuelve a donde estaba
  it('con destino, al crear la cuenta vuelve a ese destino', async () => {
    await preparar('/catalogo/5/entradas/1');
    completar();

    await enviar();

    expect(navegaciones).toEqual(['/catalogo/5/entradas/1']);
  });

  // HU-01 RF-2
  it('sin destino, al crear la cuenta va al inicio', async () => {
    completar();

    await enviar();

    expect(navegaciones).toEqual(['/']);
  });

  it('con destino, el enlace a iniciar sesión también lo lleva', async () => {
    await preparar('/catalogo/5/entradas/1');

    expect(iniciaSesion()).toBe('/inicio-sesion?destino=%2Fcatalogo%2F5%2Fentradas%2F1');
  });

  // HU-01 RF-2
  it('un destino externo no se sigue: va al inicio', async () => {
    await preparar('//sitio-ajeno.example/phishing');
    completar();

    await enviar();

    expect(navegaciones).toEqual(['/']);
    expect(iniciaSesion()).toBe('/inicio-sesion');
  });

  it('no muestra el lema de la marca', () => {
    expect(texto()).not.toContain('Santa Cruz');
  });

  it('pide los datos del registro, cada uno con su etiqueta', () => {
    const etiquetas: HTMLLabelElement[] = Array.from(fijo.nativeElement.querySelectorAll('label'));

    expect(etiquetas.map((etiqueta) => etiqueta.htmlFor)).toEqual([
      'nombre',
      'apellido',
      'correo',
      'contrasena',
      'telefono',
    ]);
    expect(campo('contrasena').type).toBe('password');
  });

  // HU-01 RF-1, RF-2
  it('con los datos completos crea la cuenta y deja la sesión iniciada', async () => {
    completar({ telefono: '70011122' });

    await enviar();

    expect(autenticacion.llamadas).toEqual([
      {
        nombre: 'Ana',
        apellido: 'Rojas',
        correo: 'ana@mail.com',
        contrasena: 'secreta12',
        telefono: '70011122',
      },
    ]);
    expect(sesion.iniciadas).toEqual([SESION]);
    expect(navegaciones).toEqual(['/']);
  });

  // HU-01 RF-10: la cuenta se puede registrar sin teléfono
  it('registra sin teléfono', async () => {
    completar();

    await enviar();

    expect(autenticacion.llamadas[0].telefono).toBe('');
    expect(navegaciones.length).toBe(1);
  });

  // HU-01 RF-3: nombre, apellido, correo y contraseña son obligatorios
  it('con todo vacío no llama a la API y marca los cuatro campos obligatorios', async () => {
    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('Escribe tu nombre');
    expect(texto()).toContain('Escribe tu apellido');
    expect(texto()).toContain('Escribe tu correo');
    expect(texto()).toContain('Elige una contraseña');
    expect(navegaciones).toEqual([]);
  });

  // HU-01 RF-5
  it('avisa cuando el correo no tiene formato de correo', async () => {
    completar({ correo: 'ana-sin-arroba' });

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('formato de correo');
  });

  // La ayuda acompaña mientras falte cumplir el mínimo; cumplido, estorba
  it('la ayuda de la contraseña se va al llegar a los 8 caracteres', () => {
    expect(texto()).toContain('Al menos 8 caracteres');

    escribir('contrasena', 'siete12');
    expect(texto()).toContain('Al menos 8 caracteres');

    escribir('contrasena', 'secreta12');
    expect(texto()).not.toContain('Al menos 8 caracteres');
  });

  // HU-01 RF-6: mínimo de 8 caracteres
  it('frena una contraseña de 7 caracteres sin llamar a la API', async () => {
    completar({ contrasena: 'siete12' });

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('8 caracteres');
  });

  // HU-01 RF-6: máximo de 72 bytes, que es lo que admite BCrypt
  it('frena una contraseña de más de 72 bytes', async () => {
    completar({ contrasena: 'a'.repeat(73) });

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('demasiado larga');
  });

  // HU-01 RF-6
  it('acepta una contraseña de exactamente 72 bytes', async () => {
    completar({ contrasena: 'a'.repeat(72) });

    await enviar();
    await new Promise((seguir) => setTimeout(seguir, 0));
    await fijo.whenStable();
    fijo.detectChanges();

    expect(autenticacion.llamadas.length).toBe(1);
  });

  // HU-01 RF-4: longitudes de la tabla usuario
  it('frena un nombre de más de 80 caracteres', async () => {
    completar({ nombre: 'a'.repeat(81) });

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('80 caracteres');
  });

  // HU-01 RF-9: el backend indica todos los campos a corregir, y cada mensaje se
  // muestra debajo del campo que le toca
  it('reparte los errores del backend campo por campo', async () => {
    autenticacion.respuesta = rechaza({
      status: 400,
      detail: 'Hay campos a corregir.',
      codigo: 'DATOS_INVALIDOS',
      errores: [
        { campo: 'telefono', mensaje: 'admite hasta 20 caracteres' },
        { campo: 'nombre', mensaje: 'admite hasta 80 caracteres' },
      ],
    });
    completar();

    await enviar();

    expect(mensajeDe('telefono')).toContain('El teléfono admite hasta 20 caracteres.');
    expect(mensajeDe('nombre')).toContain('El nombre admite hasta 80 caracteres.');
    expect(navegaciones).toEqual([]);
  });

  // La respuesta del backend describe los datos que se enviaron: si se corrige
  // uno, su mensaje deja de valer y no puede quedar pegado en la pantalla
  it('el mensaje del backend desaparece al corregir el campo', async () => {
    autenticacion.respuesta = rechaza({
      status: 400,
      detail: 'Hay campos a corregir.',
      codigo: 'DATOS_INVALIDOS',
      errores: [{ campo: 'nombre', mensaje: 'admite hasta 80 caracteres' }],
    });
    completar();

    await enviar();
    expect(mensajeDe('nombre')).toContain('El nombre admite hasta 80 caracteres.');

    escribir('nombre', 'Ana María');

    expect(mensajeDe('nombre')).toBe('');
    expect(texto()).not.toContain('Revisa los datos marcados');
  });

  // HU-01 RF-8: el aviso vale para el correo que se envió, no para el que se está
  // escribiendo ahora
  it('el aviso de cuenta existente desaparece al cambiar el correo', async () => {
    autenticacion.respuesta = rechaza({ status: 409, detail: 'x', codigo: 'CUENTA_EXISTENTE' });
    completar();

    await enviar();
    expect(texto()).toContain('Ya existe una cuenta con ese correo.');

    escribir('correo', 'otra@mail.com');

    expect(texto()).not.toContain('Ya existe una cuenta con ese correo.');
  });

  // HU-01 RF-3: campos con solo espacios son dato incompleto, igual que vacíos
  it('un nombre de solo espacios no llega a la API', async () => {
    completar({ nombre: '   ' });

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('Escribe tu nombre');
  });

  // La cuenta ya quedó creada: decir que falló el registro haría reintentar y
  // chocar contra un 409
  it('si falla la navegación no dice que falló el registro', async () => {
    const router = TestBed.inject(Router);
    router.navigateByUrl = () => Promise.reject(new Error('navegación interrumpida'));
    // El fallo llega un tick después de que submit() resuelve
    completar();

    await enviar();
    await new Promise((seguir) => setTimeout(seguir, 0));
    await fijo.whenStable();
    fijo.detectChanges();

    expect(autenticacion.llamadas.length).toBe(1);
    expect(texto()).not.toContain('No pudimos conectar con el servidor.');
    // Y la pantalla no se queda trabada en "Creando cuenta…"
    expect(fijo.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
  });

  // HU-01 RF-8: el correo ya pertenece a una cuenta
  it('avisa que ya existe una cuenta con ese correo', async () => {
    autenticacion.respuesta = rechaza({
      status: 409,
      detail: 'detalle técnico del backend',
      codigo: 'CUENTA_EXISTENTE',
    });
    completar();

    await enviar();

    expect(texto()).toContain('Ya existe una cuenta con ese correo.');
    expect(texto()).not.toContain('detalle técnico');
    expect(sesion.iniciadas).toEqual([]);
    expect(navegaciones).toEqual([]);
  });

  // HU-01 RF-8: el correo es el único dato a cambiar
  it('con cuenta existente el foco va al correo', async () => {
    autenticacion.respuesta = rechaza({ status: 409, detail: 'x', codigo: 'CUENTA_EXISTENTE' });
    completar();

    await enviar();

    expect(document.activeElement).toBe(campo('correo'));
  });

  // HU-01 RF-8: un doble clic no manda dos registros
  it('deshabilita el botón mientras se envía', async () => {
    let responder: (sesion: Sesion) => void = () => undefined;
    autenticacion.respuesta = new Promise((resolver) => (responder = resolver));
    completar();

    fijo.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    fijo.detectChanges();

    expect(fijo.nativeElement.querySelector('button[type="submit"]').disabled).toBe(true);
    responder(SESION);
    await fijo.whenStable();
  });

  it('avisa cuando no se pudo contactar al servidor', async () => {
    autenticacion.respuesta = rechaza({
      status: 0,
      detail: 'No se pudo contactar al servidor',
      codigo: 'SIN_CONEXION',
    });
    completar();

    await enviar();

    expect(texto()).toContain('No pudimos conectar con el servidor.');
  });

  it('el aviso general se anuncia como alerta', async () => {
    autenticacion.respuesta = rechaza({ status: 409, detail: 'x', codigo: 'CUENTA_EXISTENTE' });
    completar();

    await enviar();

    const alerta = fijo.nativeElement.querySelector('[role="alert"]');
    expect(alerta.textContent).toContain('Ya existe una cuenta con ese correo.');
  });

  it('ofrece volver a iniciar sesión', () => {
    const enlace: HTMLAnchorElement = fijo.nativeElement.querySelector('.enlace-cruzado a');

    expect(enlace.getAttribute('href')).toBe('/inicio-sesion');
  });

  // HU-04 RF-12
  it('la contraseña tiene el botón para verla', () => {
    const boton: HTMLButtonElement = fijo.nativeElement.querySelector('button.ver-contrasena');

    boton.click();
    fijo.detectChanges();

    expect(campo('contrasena').type).toBe('text');
  });

  it('no menciona la universidad', () => {
    expect(texto().toLowerCase()).not.toContain('universidad');
  });

  // Cinco alertas a la vez se pisan en el lector de pantalla: los errores de
  // campo se leen al recibir el foco (aria-describedby), no como alerta
  it('los errores de campo no se anuncian todos juntos', async () => {
    await enviar();

    expect(fijo.nativeElement.querySelectorAll('.error').length).toBeGreaterThan(1);
    expect(fijo.nativeElement.querySelector('.error[role]')).toBeNull();
  });
});
