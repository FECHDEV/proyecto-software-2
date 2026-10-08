import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DialogoConfirmacion } from './dialogo-confirmacion';

const DESACTIVAR = {
  titulo: 'Desactivar cuenta',
  mensaje: 'Ana Rojas (ana@mail.com) no podrá iniciar sesión hasta que la reactives.',
  confirmar: 'Desactivar',
};

describe('DialogoConfirmacion', () => {
  let fijo: ComponentFixture<DialogoConfirmacion>;

  beforeEach(async () => {
    TestBed.resetTestingModule();
    fijo = TestBed.createComponent(DialogoConfirmacion);
    // La app todavía usa zone.js: sin esto whenStable() no vuelve a pintar
    fijo.autoDetectChanges();
    await fijo.whenStable();
  });

  function dialogo(): HTMLDialogElement {
    return fijo.nativeElement.querySelector('dialog');
  }

  function boton(texto: string): HTMLButtonElement {
    const botones: HTMLButtonElement[] = Array.from(fijo.nativeElement.querySelectorAll('button'));
    const encontrado = botones.find((cada) => cada.textContent?.trim() === texto);
    if (encontrado === undefined) {
      throw new Error(`No hay botón «${texto}»`);
    }
    return encontrado;
  }

  // Envuelta en un objeto: una función async que devuelve una promesa la
  // aplana, y el await esperaría la respuesta del diálogo en vez de su apertura
  async function abrir(): Promise<{ respuesta: Promise<boolean> }> {
    const respuesta = fijo.componentInstance.abrir(DESACTIVAR);
    await fijo.whenStable();
    return { respuesta };
  }

  it('empieza cerrado', () => {
    expect(dialogo().hasAttribute('open')).toBe(false);
  });

  it('se abre con el título, el mensaje y la acción pedidos', async () => {
    await abrir();

    expect(dialogo().hasAttribute('open')).toBe(true);
    expect(dialogo().textContent).toContain('Desactivar cuenta');
    expect(dialogo().textContent).toContain('ana@mail.com');
    expect(boton('Desactivar')).toBeTruthy();
  });

  it('sin otro texto, el botón para desistir dice Cancelar', async () => {
    await abrir();

    expect(boton('Cancelar')).toBeTruthy();
  });

  // Cuando la acción misma es cancelar algo, «Cancelar» junto a «Cancelar
  // evento» confunde: quien abre el diálogo puede nombrar la salida segura
  it('el botón para desistir puede decir otra cosa', async () => {
    fijo.componentInstance.abrir({
      titulo: 'Cancelar evento',
      mensaje: 'Es definitivo.',
      confirmar: 'Cancelar evento',
      desistir: 'No, mantenerlo',
    });
    await fijo.whenStable();

    expect(boton('No, mantenerlo')).toBeTruthy();
    expect(() => boton('Cancelar')).toThrow();
  });

  // Un lector de pantalla anuncia el título y el mensaje al abrirse
  it('el título y el mensaje describen el diálogo', async () => {
    await abrir();

    const titulo = document.getElementById(dialogo().getAttribute('aria-labelledby') ?? '');
    const mensaje = document.getElementById(dialogo().getAttribute('aria-describedby') ?? '');
    expect(titulo?.textContent).toContain('Desactivar cuenta');
    expect(mensaje?.textContent).toContain('no podrá iniciar sesión');
  });

  it('confirmar responde que sí y cierra', async () => {
    const { respuesta } = await abrir();

    boton('Desactivar').click();

    await expect(respuesta).resolves.toBe(true);
    expect(dialogo().hasAttribute('open')).toBe(false);
  });

  it('cancelar responde que no y cierra', async () => {
    const { respuesta } = await abrir();

    boton('Cancelar').click();

    await expect(respuesta).resolves.toBe(false);
    expect(dialogo().hasAttribute('open')).toBe(false);
  });

  // Escape cierra el <dialog> sin pasar por ningún botón
  it('cerrarlo sin elegir cuenta como cancelar', async () => {
    const { respuesta } = await abrir();

    dialogo().close();

    await expect(respuesta).resolves.toBe(false);
  });

  // Quien usa teclado sigue donde estaba, no al principio de la página
  it('al cerrarse devuelve el foco a quien lo abrió', async () => {
    const origen = document.createElement('button');
    document.body.appendChild(origen);
    origen.focus();
    const { respuesta } = await abrir();

    boton('Cancelar').click();
    await respuesta;

    expect(document.activeElement).toBe(origen);
    origen.remove();
  });
});
