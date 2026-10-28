import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, TitleStrategy, withNavigationErrorHandler } from '@angular/router';

import { routes } from './app.routes';
import { erroresInterceptor } from './core/http/errores.interceptor';
import { provideDesplazamiento } from './core/navegacion/desplazamiento';
import { TituloYDescripcion } from './core/navegacion/titulo-y-descripcion';
import { provideVersionPublicada, recargarSiFaltaElFragmento } from './core/navegacion/version-nueva';
import { tokenInterceptor } from './core/http/token.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Una pestaña abierta antes de publicar una versión nueva recarga en lugar de quedarse sin navegar
    provideRouter(routes, withNavigationErrorHandler(recargarSiFaltaElFragmento)),
    // Con el título, la descripción para los buscadores
    { provide: TitleStrategy, useClass: TituloYDescripcion },
    provideVersionPublicada(),
    // Otra ruta empieza arriba; los filtros no mueven la página
    provideDesplazamiento(),
    // El orden importa: el token se agrega antes de que la respuesta pase por
    // el manejo de errores
    provideHttpClient(withInterceptors([tokenInterceptor, erroresInterceptor])),
  ],
};
