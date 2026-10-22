import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthStore } from '@core/services/auth-store';
import { isApiRequest, isPublicApiRequest } from './api-request';

/**
 * Attaches `Authorization: Bearer <jwt>` to calls to our own backend while someone is signed in. It never adds the
 * header to another origin (the token must not leak), to sign-in and registration (they do not need it), or over a
 * header the caller already set.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthStore).token();
  if (token === null || req.headers.has('Authorization') || !isApiRequest(req.url) || isPublicApiRequest(req.url)) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
