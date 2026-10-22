import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '@core/services/auth-store';
import { resolveReturnUrl } from '@core/services/role-home';

/**
 * Guards login and register: anonymous visitors only. Someone already signed in belongs on their own home page (or on
 * the page a `returnUrl` names, when their role may open it), so `/` and a stale login link both end up somewhere useful.
 */
export const guestGuard: CanActivateFn = (route) => {
  const auth = inject(AuthStore);
  const role = auth.hasValidSession() ? auth.role() : null;
  if (role === null) {
    return true;
  }

  const router = inject(Router);
  return router.parseUrl(resolveReturnUrl(route.queryParamMap.get('returnUrl'), role));
};
