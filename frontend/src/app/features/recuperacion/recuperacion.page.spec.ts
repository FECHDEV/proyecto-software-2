import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { ErrorApi } from '../../core/modelos/error-api';
import { RecuperacionPage } from './recuperacion.page';

class AutenticacionFalsa {
  respuesta: Promise<void> = Promise.resolve();
  correos: string[] = [];

  solicitarRecuperacion(correo: string): Promise<void> {
    this.correos.push(correo);
    return this.respuesta;
  }
}

function rechaza(codigo: string, status: number): Promise<void> {
  const error: ErrorApi = { status, detail: 'detalle técnico del backend', codigo };
  return Promise.reject(error);
}

describe('RecuperacionPage', () => {
  let fijo: ComponentFixture<RecuperacionPage>;
  let autenticacion: AutenticacionFalsa;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    autenticacion = new AutenticacionFalsa();

    await TestBed.configureTestingModule({
      imports: [RecuperacionPage],
      providers: [provideRouter([]), { provide: AutenticacionService, useValue: autenticacion }],
    }).compileComponents();

    fijo = TestBed.createComponent(RecuperacionPage);
    fijo.detectChanges();
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

  async function enviar() {
    fijo.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
    await fijo.whenStable();
    fijo.detectChanges();
  }

  function texto(): string {
    return fijo.nativeElement.textContent as string;
  }

  const CONFIRMACION = 'Si ese correo tiene una cuenta, te enviamos las instrucciones';

  it('pide el correo de la cuenta', () => {
    const etiqueta: HTMLLabelElement = fijo.nativeElement.querySelector('label');

    expect(etiqueta.htmlFor).toBe('correo');
    expect(campo('correo').type).toBe('email');
  });

  // HU-03 RF-1, RF-2: se pide sin sesión iniciada
  it('pide la recuperación y confirma sin decir si la cuenta existe', async () => {
    escribir('correo', 'ana@mail.com');

    await enviar();

    expect(autenticacion.correos).toEqual(['ana@mail.com']);
    expect(texto()).toContain(CONFIRMACION);
    expect(fijo.nativeElement.querySelector('form')).toBeNull();
    // El formulario que tenía el foco desapareció: la confirmación puede recibirlo
    expect(fijo.nativeElement.querySelector('.aviso.exito').getAttribute('tabindex')).toBe('-1');
  });

  // HU-03 RF-2: un correo sin cuenta recibe exactamente la misma respuesta
  it('un correo sin cuenta ve la misma confirmación, palabra por palabra', async () => {
    escribir('correo', 'nadie@mail.com');

    await enviar();

    expect(texto()).toContain(CONFIRMACION);
    expect(texto()).not.toContain('no existe');
    expect(texto()).not.toContain('no encontramos');
  });

  it('sin correo no llama a la API', async () => {
    await enviar();

    expect(autenticacion.correos).toEqual([]);
    expect(texto()).toContain('Escribe tu correo');
  });

  it('avisa cuando el correo no tiene formato de correo', async () => {
    escribir('correo', 'ana-sin-arroba');

    await enviar();

    expect(autenticacion.correos).toEqual([]);
    expect(texto()).toContain('formato de correo');
  });

  // HU-03 RF-7: el límite de solicitudes
  it('muestra el aviso del límite y deja el formulario para reintentar', async () => {
    autenticacion.respuesta = rechaza('SOLICITUDES_EXCEDIDAS', 429);
    escribir('correo', 'ana@mail.com');

    await enviar();

    expect(texto()).toContain('Espera unos minutos');
    expect(texto()).not.toContain(CONFIRMACION);
    expect(texto()).not.toContain('detalle técnico');
    expect(fijo.nativeElement.querySelector('form')).not.toBeNull();
  });

  it('avisa cuando no se pudo contactar al servidor', async () => {
    autenticacion.respuesta = rechaza('SIN_CONEXION', 0);
    escribir('correo', 'ana@mail.com');

    await enviar();

    expect(texto()).toContain('No pudimos conectar con el servidor.');
    expect(texto()).not.toContain(CONFIRMACION);
  });

  it('ofrece volver a iniciar sesión', () => {
    const enlace: HTMLAnchorElement = fijo.nativeElement.querySelector('.enlace-cruzado a');

    expect(enlace.getAttribute('href')).toBe('/inicio-sesion');
  });
});
