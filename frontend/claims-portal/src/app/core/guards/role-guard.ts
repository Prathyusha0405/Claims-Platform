import { inject } from '@angular/core';
import { CanMatchFn, Router, UrlSegment } from '@angular/router';

import { Role } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';

/**
 * Role gate for a lazy area, e.g. `canMatch: [roleGuard('OFFICER')]`. Because it runs at *match* time the area's code
 * is not even downloaded for the wrong role.
 *  - signed out → login, remembering the page they asked for (`returnUrl`)
 *  - signed in with a role in `allowed` → the area opens
 *  - signed in with another role → `/forbidden`
 *
 * (Returning `false` would not do: the route would just "not match" and the visitor would see the not-found page.)
 */
export const roleGuard =
  (...allowed: Role[]): CanMatchFn =>
  (_route, segments) => {
    const auth = inject(AuthStore);
    const router = inject(Router);

    if (!auth.hasValidSession()) {
      auth.discard();
      return router.createUrlTree(['/auth/login'], { queryParams: { returnUrl: requestedUrl(router, segments) } });
    }

    const role = auth.role();
    return role !== null && allowed.includes(role) ? true : router.createUrlTree(['/forbidden']);
  };

/** The URL the visitor typed or clicked, query string included; the matched segments are the fallback. */
function requestedUrl(router: Router, segments: UrlSegment[]): string {
  const navigation = router.currentNavigation();
  return navigation ? router.serializeUrl(navigation.extractedUrl) : '/' + segments.map((segment) => segment.path).join('/');
}
