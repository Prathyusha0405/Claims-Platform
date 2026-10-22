import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  convertToParamMap,
  PartialMatchRouteSnapshot,
  Route,
  Router,
  RouterStateSnapshot,
  UrlSegment,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { Role } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { authGuard } from './auth-guard';
import { guestGuard } from './guest-guard';
import { roleGuard } from './role-guard';

describe('guards', () => {
  let store: AuthStore;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), ...provideHttpTesting()] });
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => {
    store.discard();
    vi.useRealTimers();
  });

  const run = <T>(fn: () => T): T => TestBed.runInInjectionContext(fn);
  const serialize = (tree: unknown): string => TestBed.inject(Router).serializeUrl(tree as UrlTree);

  const stateAt = (url: string) => ({ url }) as RouterStateSnapshot;
  const routeWith = (returnUrl?: string) =>
    ({ queryParamMap: convertToParamMap(returnUrl === undefined ? {} : { returnUrl }) }) as ActivatedRouteSnapshot;
  const segments = (...paths: string[]) => paths.map((path) => new UrlSegment(path, {}));
  const match = (role: Role | Role[], ...paths: string[]) =>
    run(() => roleGuard(...(Array.isArray(role) ? role : [role]))({} as Route, segments(...paths), {} as PartialMatchRouteSnapshot));

  describe('authGuard', () => {
    it('lets a signed-in user through', () => {
      signInAs('CLAIMANT');

      expect(run(() => authGuard(routeWith(), stateAt('/claimant/my-claims')))).toBe(true);
    });

    it('sends a visitor to login and remembers where they were going', () => {
      const result = run(() => authGuard(routeWith(), stateAt('/claimant/claims/CLM-2026-000001?tab=history')));

      expect(result).toBeInstanceOf(UrlTree);
      expect(serialize(result)).toBe('/auth/login?returnUrl=%2Fclaimant%2Fclaims%2FCLM-2026-000001%3Ftab%3Dhistory');
    });

    it('treats an expired session as signed out and drops it', () => {
      vi.useFakeTimers({ toFake: ['Date'] }); // move the clock without letting the expiry timer sign anyone out
      signInAs('CLAIMANT', {}, 3600);
      vi.setSystemTime(Date.now() + 3601 * 1000);

      const result = run(() => authGuard(routeWith(), stateAt('/claimant/my-claims')));

      expect(result).toBeInstanceOf(UrlTree);
      expect(store.isAuthenticated()).toBe(false);
    });
  });

  describe('guestGuard', () => {
    it('keeps login and register open to visitors', () => {
      expect(run(() => guestGuard(routeWith(), stateAt('/auth/login')))).toBe(true);
    });

    it.each([
      ['CLAIMANT', '/claimant/my-claims'],
      ['OFFICER', '/officer/work-queue'],
      ['ADMIN', '/admin/dashboard'],
    ] as const)('sends a signed-in %s to their own home page', (role, home) => {
      signInAs(role);

      expect(serialize(run(() => guestGuard(routeWith(), stateAt('/auth/login'))))).toBe(home);
    });

    it('goes to the requested page instead when the role may open it', () => {
      signInAs('CLAIMANT');

      expect(serialize(run(() => guestGuard(routeWith('/claimant/claims/CLM-2026-000001'), stateAt('/auth/login'))))).toBe(
        '/claimant/claims/CLM-2026-000001',
      );
    });

    it("ignores a returnUrl into another role's area or another site", () => {
      signInAs('CLAIMANT');

      expect(serialize(run(() => guestGuard(routeWith('/admin/dashboard'), stateAt('/auth/login'))))).toBe('/claimant/my-claims');
      expect(serialize(run(() => guestGuard(routeWith('https://evil.example'), stateAt('/auth/login'))))).toBe('/claimant/my-claims');
    });
  });

  describe('roleGuard', () => {
    it('opens the area for its role', () => {
      signInAs('OFFICER');

      expect(match('OFFICER', 'officer', 'work-queue')).toBe(true);
    });

    it('opens the area for any of several roles', () => {
      signInAs('ADMIN');

      expect(match(['OFFICER', 'ADMIN'], 'admin', 'dashboard')).toBe(true);
    });

    it.each([
      ['CLAIMANT', 'OFFICER'],
      ['CLAIMANT', 'ADMIN'],
      ['OFFICER', 'ADMIN'],
      ['OFFICER', 'CLAIMANT'],
      ['ADMIN', 'OFFICER'],
      ['ADMIN', 'CLAIMANT'],
    ] as const)('sends a signed-in %s who opens the %s area to /forbidden', (signedInAs, area) => {
      signInAs(signedInAs);

      const result = match(area, area.toLowerCase(), 'anything');

      expect(result).toBeInstanceOf(UrlTree);
      expect(serialize(result)).toBe('/forbidden');
    });

    it('sends a visitor to login with the page they asked for, rather than to "not found"', () => {
      const result = match('OFFICER', 'officer', 'claims', 'CLM-2026-000001');

      expect(result).toBeInstanceOf(UrlTree);
      expect(serialize(result)).toBe('/auth/login?returnUrl=%2Fofficer%2Fclaims%2FCLM-2026-000001');
    });

    it('treats an expired session as signed out', () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      signInAs('OFFICER', {}, 3600);
      vi.setSystemTime(Date.now() + 3601 * 1000);

      expect(serialize(match('OFFICER', 'officer', 'work-queue'))).toBe('/auth/login?returnUrl=%2Fofficer%2Fwork-queue');
      expect(store.isAuthenticated()).toBe(false);
    });
  });
});
