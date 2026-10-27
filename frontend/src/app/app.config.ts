import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withNavigationErrorHandler } from '@angular/router';

import { routes } from './app.routes';
import { erroresInterceptor } from './core/http/errores.interceptor';
import { provideDesplazamiento } from './core/navegacion/desplazamiento';
import { provideVersionPublicada, recargarSiFaltaElFragmento } from './core/navegacion/version-nueva';
import { tokenInterceptor } from './core/http/token.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Una pestaña abierta antes de publicar una versión nueva recarga en lugar de quedarse sin navegar
    provideRouter(routes, withNavigationErrorHandler(recargarSiFaltaElFragmento)),
    provideVersionPublicada(),
    // Otra ruta empieza arriba; los filtros no mueven la página
    provideDesplazamiento(),
    // El orden importa: el token se agrega antes de que la respuesta pase por
    // el manejo de errores
    provideHttpClient(withInterceptors([tokenInterceptor, erroresInterceptor])),
  ],
};
