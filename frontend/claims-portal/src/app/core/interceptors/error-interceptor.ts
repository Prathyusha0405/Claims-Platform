import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '@core/models/api-error';
import { AuthStore } from '@core/services/auth-store';
import { Notify } from '@core/services/notify';
import { isPublicApiRequest } from './api-request';
import { HANDLE_UNAUTHORIZED } from './http-context';

/**
 * Turns every failed HTTP call into an {@link ApiError} (so no component sees `HttpErrorResponse`) and applies the
 * two app-wide reactions:
 *  - 401 on a protected call: the session is no longer valid, so sign out and go to login. A 401 from sign-in itself
 *    only means "wrong email or password" and is left for the login form to show.
 *  - 403: tell the user they are not allowed, then leave them where they are.
 * Everything else (404, 409, 503, no network, ...) is passed on for the page to present in context.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const notify = inject(Notify);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      const apiError = ApiError.from(error);
      if (apiError.isUnauthorized && req.context.get(HANDLE_UNAUTHORIZED) && !isPublicApiRequest(req.url)) {
        auth.signOut('unauthorized');
      } else if (apiError.isForbidden) {
        notify.error('You do not have permission to do that.');
      }
      return throwError(() => apiError);
    }),
  );
};
