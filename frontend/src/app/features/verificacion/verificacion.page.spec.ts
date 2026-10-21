import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { AutenticacionService } from '../../core/api/autenticacion.service';
import { VerificacionPage } from './verificacion.page';

class AutenticacionFalsa {
  respuesta: Promise<void> = Promise.resolve();
  readonly verificarCorreo = vi.fn((_token: string) => this.respuesta);
}

// HU-20: la página del enlace «Confirme su correo»
describe('VerificacionPage', () => {
  let fijo: ComponentFixture<VerificacionPage>;
  let autenticacion: AutenticacionFalsa;

  async function abrir(token: string | null, configurar: (falsa: AutenticacionFalsa) => void = () => {}) {
    TestBed.resetTestingModule();
    autenticacion = new AutenticacionFalsa();
    configurar(autenticacion);
    await TestBed.configureTestingModule({
      imports: [VerificacionPage],
      providers: [
        provideRouter([]),
        { provide: AutenticacionService, useValue: autenticacion },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap(token === null ? {} : { token }) } },
        },
      ],
    }).compileComponents();
    fijo = TestBed.createComponent(VerificacionPage);
    fijo.detectChanges();
    await fijo.whenStable();
    fijo.detectChanges();
  }

  function texto(): string {
    return (fijo.nativeElement.textContent as string).replace(/\s+/g, ' ');
  }

  // HU-20 RF-4
  it('verifica el token y ofrece ir a la tienda', async () => {
    await abrir('token-del-enlace');

    expect(autenticacion.verificarCorreo).toHaveBeenCalledWith('token-del-enlace');
    expect(texto()).toContain('Su correo quedó verificado');
    expect(fijo.nativeElement.querySelector('a[href="/"]')?.textContent).toContain('Ir a la tienda');
  });

  // HU-20 RF-4: mientras responde
  it('avisa mientras verifica', async () => {
    await abrir('token-del-enlace', (falsa) => (falsa.respuesta = new Promise(() => {})));

    expect(texto()).toContain('Verificando su correo…');
  });

  // HU-20 RF-5: vencido, usado o de una cuenta desactivada dicen lo mismo
  it('un enlace que no sirve lo dice y explica dónde pedir otro', async () => {
    await abrir('token-usado', (falsa) => {
      falsa.respuesta = Promise.reject({ status: 400, detail: 'detalle', codigo: 'TOKEN_VERIFICACION_INVALIDO' });
    });

    expect(texto()).toContain('Este enlace ya no sirve');
    expect(texto()).toContain('carrito o en Mis datos');
    expect(texto()).not.toContain('quedó verificado');
  });

  // HU-20 RF-5
  it('sin token muestra el mismo mensaje sin llamar a la API', async () => {
    await abrir('   ');

    expect(autenticacion.verificarCorreo).not.toHaveBeenCalled();
    expect(texto()).toContain('Este enlace ya no sirve');
  });

  // HU-20 RF-5: cualquier otro rechazo del enlace (datos inválidos, límite de un proxy) dice lo mismo, sin reintentar
  it('otro rechazo del enlace muestra el mismo mensaje sin Reintentar', async () => {
    for (const rechazo of [
      { status: 400, detail: 'detalle', codigo: 'DATOS_INVALIDOS' },
      { status: 429, detail: 'detalle' },
    ]) {
      await abrir('token-raro', (falsa) => (falsa.respuesta = Promise.reject(rechazo)));

      expect(texto()).toContain('Este enlace ya no sirve');
      expect(fijo.nativeElement.querySelector('button')).toBeNull();
    }
  });

  // Caso borde: un error de red no es un enlace inválido
  it('sin conexión deja reintentar', async () => {
    await abrir('token-del-enlace', (falsa) => {
      falsa.respuesta = Promise.reject({ status: 0, detail: 'sin red', codigo: 'SIN_CONEXION' });
    });
    expect(texto()).toContain('No pudimos conectar con el servidor');
    autenticacion.respuesta = Promise.resolve();

    (fijo.nativeElement.querySelector('button') as HTMLButtonElement).click();
    await fijo.whenStable();
    fijo.detectChanges();

    expect(autenticacion.verificarCorreo).toHaveBeenCalledTimes(2);
    expect(texto()).toContain('Su correo quedó verificado');
  });

  // Caso borde: «Reintentar» desaparece al tocarlo; el foco va al resultado, no se pierde
  it('al reintentar el foco va al resultado', async () => {
    await abrir('token-del-enlace', (falsa) => {
      falsa.respuesta = Promise.reject({ status: 0, detail: 'sin red', codigo: 'SIN_CONEXION' });
    });
    autenticacion.respuesta = Promise.resolve();
    const reintentar = fijo.nativeElement.querySelector('button') as HTMLButtonElement;
    reintentar.focus();

    reintentar.click();
    await fijo.whenStable();
    fijo.detectChanges();
    await new Promise((listo) => setTimeout(listo));

    expect(document.activeElement).not.toBe(document.body);
    expect(document.activeElement?.textContent).toContain('Su correo quedó verificado');
  });

  // HU-20 RF-16
  it('no muestra el token', async () => {
    await abrir('token-secreto-del-enlace');

    expect(fijo.nativeElement.innerHTML).not.toContain('token-secreto-del-enlace');
  });

  // Caso borde: volver a dibujar la página no manda otra verificación
  it('verifica una sola vez', async () => {
    await abrir('token-del-enlace');
    fijo.detectChanges();
    await fijo.whenStable();

    expect(autenticacion.verificarCorreo).toHaveBeenCalledTimes(1);
  });
});
