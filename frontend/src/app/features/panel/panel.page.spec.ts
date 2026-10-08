import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Sesion } from '../../core/modelos/sesion';
import { Rol } from '../../core/modelos/usuario';
import { SesionService } from '../../core/sesion/sesion.service';
import { PanelPage } from './panel.page';

function sesionCon(rol: Rol): Sesion {
  return {
    token: 'jwt.de.prueba',
    tipo: 'Bearer',
    expiracion: new Date(Date.now() + 3_600_000).toISOString(),
    usuario: {
      idUsuario: 2,
      nombre: 'Ana',
      apellido: 'Rojas',
      correo: 'ana@mail.com',
      telefono: null,
      rol,
      estado: 'ACTIVA',
      fechaRegistro: '2026-09-14T10:00:00',
    },
  };
}

describe('PanelPage', () => {
  let fijo: ComponentFixture<PanelPage>;
  let sesion: SesionService;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PanelPage],
      providers: [provideRouter([])],
    }).compileComponents();
    sesion = TestBed.inject(SesionService);
  });

  function crear() {
    fijo = TestBed.createComponent(PanelPage);
    fijo.detectChanges();
    return fijo.nativeElement as HTMLElement;
  }

  it('saluda a la persona por su nombre', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    expect(crear().textContent).toContain('Ana');
  });

  // Pedido en la prueba manual: el lema de la marca sobraba
  it('no muestra el lema de la marca', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().textContent).not.toContain('Santa Cruz');
  });

  it('un cliente no ve la gestión de usuarios', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    expect(crear().textContent).not.toContain('Usuarios');
  });

  // Ocultarlo es usabilidad: quien autoriza de verdad es el backend
  it('un administrador ve la gestión de usuarios', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().textContent).toContain('Usuarios');
  });

  it('todos ven sus datos personales', () => {
    sesion.iniciar(sesionCon('EMPLEADO'));

    expect(crear().textContent).toContain('Mis datos');
  });

  it('el acceso a mis datos lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('CLIENTE'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/mis-datos"]');

    expect(enlace?.textContent).toContain('Mis datos');
  });

  // La interfaz es del producto: nada de jerga interna del proyecto a la vista
  it('no muestra referencias a casos de uso', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    expect(crear().textContent).not.toMatch(/CU-\d/);
  });

  it('el acceso a usuarios lleva a su pantalla', () => {
    sesion.iniciar(sesionCon('ADMINISTRADOR'));

    const enlace = crear().querySelector<HTMLAnchorElement>('a[href="/usuarios"]');

    expect(enlace?.textContent).toContain('Usuarios');
    expect(fijo.nativeElement.textContent).not.toContain('Disponible pronto');
  });
});
