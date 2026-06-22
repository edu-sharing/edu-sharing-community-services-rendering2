import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners, provideZoneChangeDetection } from '@angular/core';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';

import { ApiConfiguration } from './api/api-configuration';
import { ADMIN_API_BASE } from './core/api.config';
import { authInterceptor } from './core/auth.interceptor';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(),
    // Native Date adapter for MatDatepicker + MatTimepicker (used by the date-range filter).
    provideNativeDateAdapter(),
    // Root URL for the generated ng-openapi-gen client. Operation paths already include
    // `/admin/...`, so the rootUrl is the bare ADMIN_API_BASE (runtime-injected, see api.config.ts).
    // The authInterceptor adds HTTP-Basic and handles 401 for these requests transparently.
    {
      provide: ApiConfiguration,
      useFactory: (base: string): ApiConfiguration => Object.assign(new ApiConfiguration(), { rootUrl: base }),
      deps: [ADMIN_API_BASE],
    },
  ],
};
