import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlTree, convertToParamMap, provideRouter } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { ErrorApi } from '../../core/modelos/error-api';
import { Sesion } from '../../core/modelos/sesion';
import { SesionService } from '../../core/sesion/sesion.service';
import { InicioSesionPage } from './inicio-sesion.page';

const SESION: Sesion = {
  token: 'jwt.de.prueba',
  tipo: 'Bearer',
  expiracion: new Date(Date.now() + 3_600_000).toISOString(),
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

class AutenticacionFalsa {
  respuesta: Promise<Sesion> = Promise.resolve(SESION);
  llamadas: Array<{ correo: string; contrasena: string }> = [];

  iniciarSesion(correo: string, contrasena: string): Promise<Sesion> {
    this.llamadas.push({ correo, contrasena });
    return this.respuesta;
  }
}

// Cuerpo como el que arma ManejadorErrores (ProblemDetail + código propio)
function rechaza(codigo: string, status = 401): Promise<Sesion> {
  const error: ErrorApi = { status, detail: 'detalle técnico del backend', codigo };
  return Promise.reject(error);
}

describe('InicioSesionPage', () => {
  let fijo: ComponentFixture<InicioSesionPage>;
  let autenticacion: AutenticacionFalsa;
  let sesion: SesionService;
  let navegaciones: string[];
  let destino: string | null;
  let parametros: Record<string, string>;

  async function preparar() {
    localStorage.clear();
    TestBed.resetTestingModule();
    autenticacion = new AutenticacionFalsa();
    navegaciones = [];

    await TestBed.configureTestingModule({
      imports: [InicioSesionPage],
      providers: [
        provideRouter([]),
        { provide: AutenticacionService, useValue: autenticacion },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(
                destino === null ? parametros : { ...parametros, destino },
              ),
            },
          },
        },
      ],
    }).compileComponents();

    const router = TestBed.inject(Router);
    router.navigateByUrl = (url: string | UrlTree) => {
      navegaciones.push(url.toString());
      return Promise.resolve(true);
    };

    sesion = TestBed.inject(SesionService);
    fijo = TestBed.createComponent(InicioSesionPage);
    fijo.detectChanges();
  }

  beforeEach(async () => {
    destino = null;
    parametros = {};
    await preparar();
  });

  function campo(id: string): HTMLInputElement {
    return fijo.nativeElement.querySelector(`#${id}`);
  }

  function escribir(id: string, valor: string) {
    const entrada = campo(id);
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

  it('no muestra el lema de la marca', () => {
    expect(texto()).not.toContain('Santa Cruz');
  });

  it('muestra los campos de correo y contraseña', () => {
    expect(campo('correo')).toBeTruthy();
    expect(campo('contrasena').type).toBe('password');
  });

  // HU-02 RF-8: sin datos no se intenta autenticar
  it('con los campos vacíos no llama a la API y marca los dos campos', async () => {
    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('Escribe tu correo');
    expect(texto()).toContain('Escribe tu contraseña');
  });

  // HU-02 RF-8
  it('avisa cuando el correo no tiene formato de correo', async () => {
    escribir('correo', 'esto-no-es-un-correo');
    escribir('contrasena', 'secreta12');

    await enviar();

    expect(autenticacion.llamadas).toEqual([]);
    expect(texto()).toContain('formato');
  });

  // HU-02 RF-1: entra y guarda la sesión
  it('con credenciales correctas inicia la sesión y entra', async () => {
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'secreta12');

    await enviar();

    expect(autenticacion.llamadas).toEqual([{ correo: 'ana@mail.com', contrasena: 'secreta12' }]);
    expect(sesion.autenticado()).toBe(true);
    expect(navegaciones).toEqual(['/']);
  });

  // HU-02 RF-3: mismo mensaje exista o no el correo
  it('muestra el mensaje de credenciales incorrectas y no entra', async () => {
    autenticacion.respuesta = rechaza('CREDENCIALES_INCORRECTAS');
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'incorrecta');

    await enviar();

    expect(texto()).toContain('El correo o la contraseña no son correctos.');
    expect(texto()).not.toContain('detalle técnico');
    expect(sesion.autenticado()).toBe(false);
    expect(navegaciones).toEqual([]);
  });

  // HU-02 RF-4: cuenta desactivada
  it('muestra el mensaje propio de la cuenta desactivada', async () => {
    autenticacion.respuesta = rechaza('CUENTA_DESACTIVADA');
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'secreta12');

    await enviar();

    expect(texto()).toContain('desactivada');
  });

  // HU-02 RF-5: bloqueo por intentos fallidos
  it('muestra cuánto hay que esperar tras demasiados intentos', async () => {
    autenticacion.respuesta = rechaza('INTENTOS_EXCEDIDOS');
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'incorrecta');

    await enviar();

    expect(texto()).toContain('15 minutos');
  });

  it('el aviso de error se anuncia como alerta', async () => {
    autenticacion.respuesta = rechaza('CREDENCIALES_INCORRECTAS');
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'incorrecta');

    await enviar();

    const alerta = fijo.nativeElement.querySelector('[role="alert"]');
    expect(alerta.textContent).toContain('El correo o la contraseña no son correctos.');
  });

  it('cada campo tiene su etiqueta visible', () => {
    const etiquetas: HTMLLabelElement[] = Array.from(
      fijo.nativeElement.querySelectorAll('label'),
    );
    expect(etiquetas.map((etiqueta) => etiqueta.htmlFor)).toEqual(['correo', 'contrasena']);
  });

  // El guard deja en la URL a dónde iba la persona
  // HU-02 RF-1
  it('vuelve al destino que la persona quería', async () => {
    destino = '/usuarios';
    await preparar();
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'secreta12');

    await enviar();

    expect(navegaciones).toEqual(['/usuarios']);
  });

  // El guard guarda la URL completa: los parámetros tienen que volver intactos
  it('conserva los parámetros del destino', async () => {
    destino = '/eventos?fecha=2026-10-01&tipo=concierto';
    await preparar();
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'secreta12');

    await enviar();

    expect(navegaciones).toEqual(['/eventos?fecha=2026-10-01&tipo=concierto']);
  });

  // Un destino externo sería un redirect abierto
  // HU-02 RF-1
  it('ignora un destino que apunte fuera del sitio', async () => {
    destino = '//sitio-ajeno.example/phishing';
    await preparar();
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'secreta12');

    await enviar();

    expect(navegaciones).toEqual(['/']);
  });

  function crearCuenta(): string | null {
    const enlace = Array.from(fijo.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>).find(
      (cada) => cada.textContent?.trim() === 'Crear cuenta',
    );
    return enlace?.getAttribute('href') ?? null;
  }

  // quien iba a comprar y no tiene cuenta vuelve igual al tipo elegido
  it('crear cuenta conserva el destino', async () => {
    destino = '/catalogo/5/entradas/1';
    await preparar();

    expect(crearCuenta()).toBe('/registro?destino=%2Fcatalogo%2F5%2Fentradas%2F1');
  });

  it('sin destino, crear cuenta va al registro a secas', () => {
    expect(crearCuenta()).toBe('/registro');
  });

  it('un destino externo no pasa al registro', async () => {
    destino = '//sitio-ajeno.example/phishing';
    await preparar();

    expect(crearCuenta()).toBe('/registro');
  });

  function botonVerContrasena(): HTMLButtonElement {
    return fijo.nativeElement.querySelector('button.ver-contrasena');
  }

  // HU-02 RF-13
  it('la contraseña empieza oculta', () => {
    expect(campo('contrasena').type).toBe('password');
    expect(botonVerContrasena().getAttribute('aria-label')).toBe('Mostrar contraseña');
  });

  // HU-02 RF-13
  it('mostrar contraseña la deja ver y ofrece ocultarla', () => {
    escribir('contrasena', 'secreta12');

    botonVerContrasena().click();
    fijo.detectChanges();

    expect(campo('contrasena').type).toBe('text');
    expect(campo('contrasena').value).toBe('secreta12');
    expect(botonVerContrasena().getAttribute('aria-label')).toBe('Ocultar contraseña');

    botonVerContrasena().click();
    fijo.detectChanges();

    expect(campo('contrasena').type).toBe('password');
  });

  // HU-02 RF-13: el botón está dentro del formulario, pero no lo envía
  it('mostrar contraseña no envía el formulario', () => {
    escribir('correo', 'ana@mail.com');
    escribir('contrasena', 'secreta12');

    botonVerContrasena().click();
    fijo.detectChanges();

    expect(botonVerContrasena().type).toBe('button');
    expect(navegaciones).toEqual([]);
  });

  it('no menciona la universidad', () => {
    expect(texto().toLowerCase()).not.toContain('universidad');
  });

  // se llega acá desde el restablecimiento de la contraseña (el registro ya
  // entra directo: HU-01 RF-2)
  describe('avisos al llegar', () => {
    // HU-02 RF-10
    it('avisa que la sesión se cerró', async () => {
      parametros = { aviso: 'sesion-cerrada' };
      await preparar();

      expect(texto()).toContain('Tu sesión se cerró. Inicia sesión de nuevo.');
    });

    // HU-02 RF-10: al volver a entrar, sigue donde estaba
    it('después del aviso de sesión cerrada vuelve al destino', async () => {
      parametros = { aviso: 'sesion-cerrada' };
      destino = '/mis-datos';
      await preparar();
      escribir('correo', 'ana@mail.com');
      escribir('contrasena', 'secreta12');

      await enviar();

      expect(navegaciones).toEqual(['/mis-datos']);
    });

    it('avisa cuando se viene de restablecer la contraseña', async () => {
      parametros = { aviso: 'contrasena-actualizada' };
      await preparar();

      expect(texto()).toContain('Tu contraseña quedó actualizada');
    });

    // El parámetro se lee de la URL: un valor cualquiera no puede pintar nada
    it('ignora un aviso desconocido', async () => {
      parametros = { aviso: 'cualquier-cosa' };
      await preparar();

      expect(texto()).not.toContain('quedó');
    });

    // Las claves heredadas de Object no son avisos
    it('ignora un aviso que sea una propiedad heredada', async () => {
      parametros = { aviso: 'constructor' };
      await preparar();

      expect(fijo.nativeElement.querySelector('.aviso.exito')).toBeNull();
    });

    it('sin esos parámetros la pantalla queda como siempre', () => {
      expect(texto()).not.toContain('quedó');
      expect(campo('correo').value).toBe('');
    });

    // El aviso no tiene por qué volver a aparecer cada vez que se recargue esa
    // dirección
    it('saca el aviso de la URL después de leerlo', async () => {
      parametros = { aviso: 'contrasena-actualizada' };
      await preparar();

      expect(TestBed.inject(Location).path()).toBe('/inicio-sesion');
    });

    it('conserva el destino al limpiar la URL', async () => {
      parametros = { aviso: 'contrasena-actualizada' };
      destino = '/usuarios';
      await preparar();

      expect(TestBed.inject(Location).path()).toBe('/inicio-sesion?destino=%2Fusuarios');
    });

    it('el aviso se va al intentar entrar', async () => {
      parametros = { aviso: 'contrasena-actualizada' };
      await preparar();
      autenticacion.respuesta = rechaza('CREDENCIALES_INCORRECTAS');
      escribir('correo', 'ana@mail.com');
      escribir('contrasena', 'incorrecta');

      await enviar();

      expect(texto()).not.toContain('Tu contraseña quedó actualizada');
      expect(texto()).toContain('El correo o la contraseña no son correctos.');
    });

    it('ofrece recuperar la contraseña', () => {
      const enlaces: HTMLAnchorElement[] = Array.from(
        fijo.nativeElement.querySelectorAll('.enlace-cruzado a'),
      );

      expect(enlaces.map((enlace) => enlace.getAttribute('href'))).toContain('/recuperacion');
    });

    it('ofrece crear una cuenta', () => {
      const enlaces: HTMLAnchorElement[] = Array.from(
        fijo.nativeElement.querySelectorAll('.enlace-cruzado a'),
      );

      expect(enlaces.map((enlace) => enlace.getAttribute('href'))).toContain('/registro');
    });
  });

  it('los errores de campo no se anuncian como alerta', async () => {
    await enviar();

    expect(fijo.nativeElement.querySelectorAll('.error').length).toBe(2);
    expect(fijo.nativeElement.querySelector('.error[role]')).toBeNull();
  });
});
