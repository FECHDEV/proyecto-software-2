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
  // La portada es el panel con los accesos de cada rol. Sin sesión, el guard
  // manda a iniciar sesión; cuando el proyecto tenga una portada pública (un
  // catálogo), va acá y el panel pasa a /panel
  {
    path: '',
    pathMatch: 'full',
    title: MARCA.nombre,
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
  { path: '**', redirectTo: '' },
];
