import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from './app.routes';
import { Sesion } from './core/modelos/sesion';
import { Rol } from './core/modelos/usuario';
import { SesionService } from './core/sesion/sesion.service';

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
      correoVerificado: true,
    },
  };
}

// Las rutas reales de la aplicación, con sus guards
describe('rutas', () => {
  async function navegarComo(rol: Rol | null, url: string): Promise<string> {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    if (rol !== null) {
      TestBed.inject(SesionService).iniciar(sesionCon(rol));
    }
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl(url);
    return TestBed.inject(Router).url;
  }

  // Las pantallas que importan @lucide/angular tardan en compilarse la primera vez dentro de
  // Vitest (el paquete trae todos los iconos; en el build solo quedan los usados). Se cargan
  // antes, para que ese costo no cuente en el tiempo de cada prueba
  beforeAll(async () => {
    await import('./features/inicio-sesion/inicio-sesion.page');
    await import('./features/categorias/categorias.page');
    await import('./features/productos/productos.page');
    await import('./features/producto/producto.page');
    await import('./features/inventario/inventario.page');
    await import('./features/stock/stock.page');
    await import('./features/despacho/despacho.page');
    await import('./features/despacho-pedido/despacho-pedido.page');
    await import('./features/no-encontrada/no-encontrada.page');
  }, 60_000);

  afterEach(() => localStorage.clear());

  // La gestión de usuarios es del administrador: los demás vuelven al inicio (usabilidad; el
  // backend igual responde 403)
  // HU-05 RF-1
  it('la gestión de usuarios es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/usuarios')).toBe('/usuarios');
    expect(await navegarComo('CLIENTE', '/usuarios')).toBe('/');
    // HU-09 RF-16: el personal sin permiso vuelve a su panel
    expect(await navegarComo('EMPLEADO', '/usuarios')).toBe('/panel');
  });

  // HU-02 RF-12: con la sesión iniciada no tiene sentido volver a entrar
  it('con sesión, iniciar sesión lleva al inicio', async () => {
    expect(await navegarComo('CLIENTE', '/inicio-sesion')).toBe('/');
    // HU-09 RF-16
    expect(await navegarComo('CONTADOR', '/inicio-sesion')).toBe('/panel');
  });

  // HU-01 RF-14
  it('con sesión, crear cuenta lleva al inicio', async () => {
    expect(await navegarComo('CLIENTE', '/registro')).toBe('/');
  });

  // HU-02 RF-12
  it('sin sesión, iniciar sesión se muestra', async () => {
    expect(await navegarComo(null, '/inicio-sesion')).toBe('/inicio-sesion');
  });

  // HU-06 RF-1: la gestión de categorías es del administrador
  it('la gestión de categorías es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/categorias')).toBe('/categorias');
    expect(await navegarComo('EMPLEADO', '/categorias')).toBe('/panel');
  });

  // HU-07 RF-1: la gestión de productos es del administrador
  it('la gestión de productos es solo del administrador', async () => {
    expect(await navegarComo('ADMINISTRADOR', '/productos')).toBe('/productos');
    expect(await navegarComo('ADMINISTRADOR', '/productos/nuevo')).toBe('/productos/nuevo');
    expect(await navegarComo('EMPLEADO', '/productos')).toBe('/panel');
    expect(await navegarComo('CLIENTE', '/productos/7')).toBe('/');
  });

  // HU-08 RF-1: el inventario es del Empleado, el Administrador y el Contador
  it('el inventario es del empleado, el administrador y el contador', async () => {
    expect(await navegarComo('EMPLEADO', '/inventario')).toBe('/inventario');
    expect(await navegarComo('CONTADOR', '/inventario/7')).toBe('/inventario/7');
    expect(await navegarComo('ADMINISTRADOR', '/inventario')).toBe('/inventario');
    expect(await navegarComo('CLIENTE', '/inventario')).toBe('/');
  });

  // HU-18 RF-1
  it('reportes es del Contador y el Administrador', async () => {
    expect(await navegarComo('CONTADOR', '/reportes')).toBe('/reportes');
    expect(await navegarComo('ADMINISTRADOR', '/reportes?seccion=clientes')).toBe('/reportes?seccion=clientes');
    expect(await navegarComo('EMPLEADO', '/reportes')).toBe('/panel');
    expect(await navegarComo('CLIENTE', '/reportes')).toBe('/');
    expect(await navegarComo(null, '/reportes')).toBe('/inicio-sesion?destino=%2Freportes');
  });

  // HU-17 RF-9: los pedidos de todos son del personal; el Cliente tiene los suyos en Mis pedidos
  it('pedidos es del personal', async () => {
    expect(await navegarComo('EMPLEADO', '/pedidos')).toBe('/pedidos');
    expect(await navegarComo('CONTADOR', '/pedidos/7')).toBe('/pedidos/7');
    expect(await navegarComo('ADMINISTRADOR', '/pedidos?estado=TODOS')).toBe('/pedidos?estado=TODOS');
    expect(await navegarComo('CLIENTE', '/pedidos')).toBe('/');
    expect(await navegarComo(null, '/pedidos')).toBe('/inicio-sesion?destino=%2Fpedidos');
  });

  // HU-05 RF-1
  it('sin sesión la gestión de usuarios lleva a iniciar sesión', async () => {
    expect(await navegarComo(null, '/usuarios')).toBe('/inicio-sesion?destino=%2Fusuarios');
  });

  // La portada es el panel: pide sesión y, sin ella, vuelve a la portada al entrar
  // HU-09 RF-1: la tienda es para todos; RF-16: el panel pide sesión
  it('la portada y el catálogo son públicos; el panel pide sesión', async () => {
    expect(await navegarComo(null, '/')).toBe('/');
    expect(await navegarComo(null, '/catalogo?q=pala')).toBe('/catalogo?q=pala');
    expect(await navegarComo('EMPLEADO', '/')).toBe('/');
    expect(await navegarComo(null, '/panel')).toBe('/inicio-sesion?destino=%2Fpanel');
    expect(await navegarComo('EMPLEADO', '/panel')).toBe('/panel');
  });

  // HU-10 RF-1
  it('el detalle del producto es público', async () => {
    expect(await navegarComo(null, '/producto/7')).toBe('/producto/7');
    expect(await navegarComo('CLIENTE', '/producto/7')).toBe('/producto/7');
  });

  // HU-11 RF-7
  it('el carrito es solo del cliente', async () => {
    expect(await navegarComo('CLIENTE', '/carrito')).toBe('/carrito');
    expect(await navegarComo(null, '/carrito')).toBe('/inicio-sesion?destino=%2Fcarrito');
    expect(await navegarComo('EMPLEADO', '/carrito')).toBe('/panel');
    expect(await navegarComo('ADMINISTRADOR', '/carrito')).toBe('/panel');
  });

  // HU-12 RF-13
  it('confirmar el pedido es solo del cliente', async () => {
    expect(await navegarComo('CLIENTE', '/confirmar-pedido')).toBe('/confirmar-pedido');
    expect(await navegarComo(null, '/confirmar-pedido')).toBe('/inicio-sesion?destino=%2Fconfirmar-pedido');
    expect(await navegarComo('EMPLEADO', '/confirmar-pedido')).toBe('/panel');
  });

  // HU-14 RF-7
  it('mis pedidos es solo del cliente', async () => {
    expect(await navegarComo('CLIENTE', '/mis-pedidos')).toBe('/mis-pedidos');
    expect(await navegarComo(null, '/mis-pedidos')).toBe('/inicio-sesion?destino=%2Fmis-pedidos');
    expect(await navegarComo('EMPLEADO', '/mis-pedidos')).toBe('/panel');
    expect(await navegarComo('ADMINISTRADOR', '/mis-pedidos')).toBe('/panel');
  });

  // HU-12 RF-13
  it('el pedido es solo del cliente', async () => {
    expect(await navegarComo('CLIENTE', '/mis-pedidos/12')).toBe('/mis-pedidos/12');
    expect(await navegarComo(null, '/mis-pedidos/12')).toBe('/inicio-sesion?destino=%2Fmis-pedidos%2F12');
    expect(await navegarComo('EMPLEADO', '/mis-pedidos/12')).toBe('/panel');
  });

  it('mis datos pide sesión', async () => {
    expect(await navegarComo(null, '/mis-datos')).toBe('/inicio-sesion?destino=%2Fmis-datos');
    expect(await navegarComo('CLIENTE', '/mis-datos')).toBe('/mis-datos');
  });

  it('las pantallas de la cuenta no piden sesión', async () => {
    expect(await navegarComo(null, '/inicio-sesion')).toBe('/inicio-sesion');
    expect(await navegarComo(null, '/registro')).toBe('/registro');
    expect(await navegarComo(null, '/recuperacion')).toBe('/recuperacion');
  });

  // HU-20 RF-4: el enlace del correo funciona con sesión y sin ella
  it('la verificación del correo es pública', async () => {
    expect(await navegarComo(null, '/verificacion')).toBe('/verificacion');
    expect(await navegarComo('CLIENTE', '/verificacion')).toBe('/verificacion');
  });

  // Revisión total: dice que no existe, sin cambiar la dirección
  it('una ruta desconocida muestra «Esta página no existe»', async () => {
    expect(await navegarComo('CLIENTE', '/no-existe')).toBe('/no-existe');
  });
});
