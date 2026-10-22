import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { authInterceptor } from '@core/interceptors/auth-interceptor';
import { errorInterceptor } from '@core/interceptors/error-interceptor';
import { AuthStore } from '@core/services/auth-store';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    // Route params and query params bind straight to component input()s.
    provideRouter(routes, withComponentInputBinding()),
    // Interceptor order matters: authInterceptor decorates the request, errorInterceptor handles the response.
    provideHttpClient(withFetch(), withInterceptors([authInterceptor, errorInterceptor])),
    // Resume a signed-in session (reload, new page load in the same tab) before the first page and guard run, so a
    // refresh on a protected page does not bounce through the login page.
    provideAppInitializer(() => inject(AuthStore).restore()),
  ],
};
