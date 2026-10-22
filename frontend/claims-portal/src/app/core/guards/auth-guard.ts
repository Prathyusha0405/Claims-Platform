import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '@core/services/auth-store';

/**
 * Protects the whole authenticated area (the AppShell route): signed-in users only. Anyone else is sent to the login
 * page, which sends them back to where they were headed (`returnUrl`) once they have signed in.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (auth.hasValidSession()) {
    return true;
  }

  auth.discard(); // an expired session is dropped here rather than left half-alive
  return inject(Router).createUrlTree(['/auth/login'], { queryParams: { returnUrl: state.url } });
};
