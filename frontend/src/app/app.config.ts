import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { erroresInterceptor } from './core/http/errores.interceptor';
import { tokenInterceptor } from './core/http/token.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    // El orden importa: el token se agrega antes de que la respuesta pase por
    // el manejo de errores
    provideHttpClient(withInterceptors([tokenInterceptor, erroresInterceptor])),
  ],
};
