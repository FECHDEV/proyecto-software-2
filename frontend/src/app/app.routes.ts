import { Routes } from '@angular/router';

import { cambiosSinGuardarGuard } from './core/guards/cambios-sin-guardar.guard';
import { rolGuard } from './core/guards/rol.guard';
import { sesionGuard } from './core/guards/sesion.guard';
import { visitanteGuard } from './core/guards/visitante.guard';
import { MARCA } from './core/marca';

// Cada pantalla se carga cuando hace falta, para que el paquete inicial no
// arrastre las pantallas de administración.
export const routes: Routes = [
  {
    path: 'inicio-sesion',
    title: `Iniciar sesión · ${MARCA.nombre}`,
    canActivate: [visitanteGuard],
    loadComponent: () =>
      import('./features/inicio-sesion/inicio-sesion.page').then((m) => m.InicioSesionPage),
  },
  {
    path: 'registro',
    title: `Crear cuenta · ${MARCA.nombre}`,
    canActivate: [visitanteGuard],
    loadComponent: () => import('./features/registro/registro.page').then((m) => m.RegistroPage),
  },
  {
    path: 'recuperacion',
    title: `Recuperar contraseña · ${MARCA.nombre}`,
    loadComponent: () =>
      import('./features/recuperacion/recuperacion.page').then((m) => m.RecuperacionPage),
  },
  {
    path: 'restablecer',
    title: `Contraseña nueva · ${MARCA.nombre}`,
    loadComponent: () =>
      import('./features/restablecer/restablecer.page').then((m) => m.RestablecerPage),
  },
  // Sin guard: el enlace del correo funciona con sesión y sin ella (HU-20 RF-4)
  {
    path: 'verificacion',
    title: `Verificar el correo · ${MARCA.nombre}`,
    loadComponent: () =>
      import('./features/verificacion/verificacion.page').then((m) => m.VerificacionPage),
  },
  // La portada es la tienda, para todos (HU-09). El panel con los accesos de
  // cada rol pasa a /panel
  {
    path: '',
    pathMatch: 'full',
    title: MARCA.nombre,
    loadComponent: () => import('./features/portada/portada.page').then((m) => m.PortadaPage),
  },
  {
    path: 'catalogo',
    title: `Catálogo · ${MARCA.nombre}`,
    loadComponent: () => import('./features/catalogo/catalogo.page').then((m) => m.CatalogoPage),
  },
  // El detalle de un producto (HU-10): público; el título lo pone la pantalla
  // con el nombre del producto
  {
    path: 'producto/:id',
    title: MARCA.nombre,
    loadComponent: () => import('./features/detalle/detalle.page').then((m) => m.DetallePage),
  },
  // El carrito es del Cliente (HU-11 RF-7): sin sesión, a iniciarla; el personal, a su panel
  {
    path: 'carrito',
    title: `Carrito · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' },
    loadComponent: () => import('./features/carrito/carrito.page').then((m) => m.CarritoPage),
  },
  // La confirmación del pedido (HU-12), del Cliente como el carrito
  {
    path: 'confirmar-pedido',
    title: `Confirmar pedido · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' },
    loadComponent: () => import('./features/confirmacion/confirmacion.page').then((m) => m.ConfirmacionPage),
  },
  // La lista de pedidos del Cliente (HU-14)
  {
    path: 'mis-pedidos',
    title: `Mis pedidos · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' },
    loadComponent: () => import('./features/mis-pedidos/mis-pedidos.page').then((m) => m.MisPedidosPage),
  },
  // La página del pedido (HU-12 RF-8): del Cliente
  {
    path: 'mis-pedidos/:id',
    title: `Pedido · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'CLIENTE' },
    loadComponent: () => import('./features/pedido/pedido.page').then((m) => m.PedidoPage),
  },
  {
    path: 'panel',
    title: `Panel · ${MARCA.nombre}`,
    canActivate: [sesionGuard],
    loadComponent: () => import('./features/panel/panel.page').then((m) => m.PanelPage),
  },
  {
    path: 'mis-datos',
    title: `Mis datos · ${MARCA.nombre}`,
    canActivate: [sesionGuard],
    canDeactivate: [cambiosSinGuardarGuard],
    loadComponent: () => import('./features/mis-datos/mis-datos.page').then((m) => m.MisDatosPage),
  },
  {
    path: 'usuarios',
    title: `Usuarios · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR' },
    loadComponent: () => import('./features/usuarios/usuarios.page').then((m) => m.UsuariosPage),
  },
  {
    path: 'categorias',
    title: `Categorías · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR' },
    loadComponent: () => import('./features/categorias/categorias.page').then((m) => m.CategoriasPage),
  },
  {
    path: 'productos',
    title: `Productos · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { rol: 'ADMINISTRADOR' },
    loadComponent: () => import('./features/productos/productos.page').then((m) => m.ProductosPage),
  },
  // «nuevo» o el número del producto: la misma pantalla, así al crear pasa a
  // editar sin volver a construirse
  {
    path: 'productos/:id',
    title: `Producto · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    canDeactivate: [cambiosSinGuardarGuard],
    data: { rol: 'ADMINISTRADOR' },
    loadComponent: () => import('./features/producto/producto.page').then((m) => m.ProductoPage),
  },
  {
    path: 'inventario',
    title: `Inventario · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] },
    loadComponent: () => import('./features/inventario/inventario.page').then((m) => m.InventarioPage),
  },
  {
    path: 'inventario/:id',
    title: `Stock · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    canDeactivate: [cambiosSinGuardarGuard],
    data: { roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] },
    loadComponent: () => import('./features/stock/stock.page').then((m) => m.StockPage),
  },
  {
    path: 'pedidos',
    title: `Pedidos · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] },
    loadComponent: () => import('./features/despacho/despacho.page').then((m) => m.DespachoPage),
  },
  {
    path: 'pedidos/:id',
    title: `Pedido · ${MARCA.nombre}`,
    canActivate: [rolGuard],
    data: { roles: ['ADMINISTRADOR', 'EMPLEADO', 'CONTADOR'] },
    loadComponent: () => import('./features/despacho-pedido/despacho-pedido.page').then((m) => m.DespachoPedidoPage),
  },
  { path: '**', redirectTo: '' },
];
