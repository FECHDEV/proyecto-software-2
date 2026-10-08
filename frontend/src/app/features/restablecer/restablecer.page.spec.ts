import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, NavigationExtras, Router, convertToParamMap, provideRouter } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { ErrorApi } from '../../core/modelos/error-api';
import { RestablecerPage } from './restablecer.page';

class AutenticacionFalsa {
  respuesta: Promise<void> = Promise.resolve();
  llamadas: Array<{ token: string; contrasena: string }> = [];

  restablecerContrasena(token: string, contrasena: string): Promise<void> {
    this.llamadas.push({ token, contrasena });
    return this.respuesta;
  }
}

function rechaza(codigo: string, status: number, errores?: ErrorApi['errores']): Promise<void> {
  return Promise.reject({ status, detail: 'detalle técnico del backend', codigo, errores });
}

describe('RestablecerPage', () => {
  let fijo: ComponentFixture<RestablecerPage>;
  let autenticacion: AutenticacionFalsa;
  let navegaciones: Array<{ ruta: unknown[]; extras?: NavigationExtras }>;
  let token: string | null;

  async function preparar() {
    TestBed.resetTestingModule();
    autenticacion = new AutenticacionFalsa();
    navegaciones = [];

    await TestBed.configureTestingModule({
      imports: [RestablecerPage],
      providers: [
        provideRouter([]),
        { provide: AutenticacionService, useValue: autenticacion },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) } },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    router.navigate = (ruta: unknown[], extras?: NavigationExtras) => {
      navegaciones.push({ ruta, extras });
      return Promise.resolve(true);
    };

    fijo = TestBed.createComponent(RestablecerPage);
    fijo.detectChanges();
  }

  beforeEach(async () => {
    token = 'token-del-enlace';
    await preparar();
  });

  function escribir(valor: string) {
    const entrada: HTMLInputElement = fijo.nativeElement.querySelector('#contrasena');
    entrada.value = valor;
    entrada.dispatchEvent(new Event('input'));
    fijo.detectChanges();
  }

  async function enviar() {
    fijo.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fijo.whenStable();
    fijo.detectChanges();
  }

  function texto(): string {
    return fijo.nativeElement.textContent as string;
  }

  it('pide la contraseña nueva', () => {
    const etiqueta: HTMLLabelElement = fijo.nativeElement.querySelector('label');

    expect(etiqueta.htmlFor).toBe('contrasena');
    expect(fijo.nativeElement.querySelector('#contrasena').type).toBe('password');
  });

  // RF-10: token vigente y contraseña nueva
  it('restablece con el token del enlace y lleva a iniciar sesión', async () => {
    escribir('nueva-clave-1');

    await enviar();

    expect(autenticacion.llamadas).toEqual([
      { token: 'token-del-enlace', contrasena: 'nueva-clave-1' },
    ]);
    expect(navegaciones).toEqual([
      { ruta: ['/inicio-sesion'], extras: { queryParams: { aviso: 'contrasena-actualizada' } } },
    ]);
  });

  // El token nunca se muestra: es tan sensible como una contraseña
  it('no escribe el token en la pantalla', () => {
    expect(texto()).not.toContain('token-del-enlace');
    expect(fijo.nativeElement.innerHTML).not.toContain('token-del-enlace');
  });

  // RF-11: la contraseña nueva tiene las mismas reglas que la del registro
  it('sin contraseña no llama a la API', async () => {
    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('Elige una contraseña');
  });

  it('frena una contraseña de 7 caracteres', async () => {
    escribir('siete12');

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('8 caracteres');
  });

  it('frena una contraseña de más de 72 bytes', async () => {
    escribir('a'.repeat(73));

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('demasiado larga');
  });

  // FA-02: token inexistente, vencido o ya usado
  it('avisa que el enlace ya no sirve y ofrece pedir otro', async () => {
    autenticacion.respuesta = rechaza('TOKEN_RECUPERACION_INVALIDO', 400);
    escribir('nueva-clave-1');

    await enviar();

    expect(texto()).toContain('Este enlace ya no sirve. Pide uno nuevo.');
    expect(texto()).not.toContain('detalle técnico');
    expect(navegaciones).toEqual([]);
    expect(enlaceA('/recuperacion')).not.toBeNull();
  });

  it('reparte los errores del backend bajo el campo', async () => {
    autenticacion.respuesta = rechaza('DATOS_INVALIDOS', 400, [
      { campo: 'contrasena', mensaje: 'debe tener al menos 8 caracteres' },
    ]);
    escribir('nueva-clave-1');

    await enviar();

    expect(fijo.nativeElement.querySelector('#error-contrasena').textContent).toContain(
      'La contraseña debe tener al menos 8 caracteres.',
    );
  });

  it('avisa cuando no se pudo contactar al servidor', async () => {
    autenticacion.respuesta = rechaza('SIN_CONEXION', 0);
    escribir('nueva-clave-1');

    await enviar();

    expect(texto()).toContain('No pudimos conectar con el servidor.');
  });

  function enlaceA(destino: string): HTMLAnchorElement | null {
    const enlaces: HTMLAnchorElement[] = Array.from(fijo.nativeElement.querySelectorAll('a'));
    return enlaces.find((enlace) => enlace.getAttribute('href') === destino) ?? null;
  }

  it('si falla la navegación no deja la pantalla trabada', async () => {
    const router = TestBed.inject(Router);
    router.navigate = () => Promise.reject(new Error('navegación interrumpida'));
    // El fallo llega un tick después de que submit() resuelve
    escribir('nueva-clave-1');

    await enviar();
    await new Promise((seguir) => setTimeout(seguir, 0));
    await fijo.whenStable();
    fijo.detectChanges();

    expect(autenticacion.llamadas.length).toBe(1);
    expect(fijo.nativeElement.querySelector('button[type="submit"]').disabled).toBe(false);
  });

  describe('sin token en la dirección', () => {
    beforeEach(async () => {
      token = null;
      await preparar();
    });

    it('no muestra el formulario y explica qué hacer', () => {
      expect(fijo.nativeElement.querySelector('form')).toBeNull();
      expect(texto()).toContain('Este enlace ya no sirve. Pide uno nuevo.');
      expect(enlaceA('/recuperacion')).not.toBeNull();
    });

    // Un role="alert" que ya está al cargar la página no se anuncia: el aviso
    // tiene que ser texto normal, que el lector de pantalla recorre solo
    it('el aviso de entrada no es una región en vivo', () => {
      expect(fijo.nativeElement.querySelector('[role="alert"]')).toBeNull();
    });
  });

  // Un enlace cortado que solo trae espacios es un enlace sin token
  it('un token de solo espacios se trata como enlace sin token', async () => {
    token = '  ';
    await preparar();

    expect(fijo.nativeElement.querySelector('form')).toBeNull();
    expect(texto()).toContain('Este enlace ya no sirve. Pide uno nuevo.');
  });
});
