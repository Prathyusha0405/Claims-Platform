import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { ApiError } from '@core/models/api-error';
import { authResponseFor, fakeJwt, fixtureUser, provideHttpTesting, signInAs } from '@testing/auth-testing';
import { AuthStore } from './auth-store';
import { Notify } from './notify';
import { TokenStorage } from './token-storage';

describe('AuthStore', () => {
  let store: AuthStore;
  let http: HttpTestingController;
  let router: Router;
  let storage: TokenStorage;
  let notify: Notify;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      // One catch-all route so the router can really be "on" a URL without any pages.
      providers: [provideRouter([{ path: '**', children: [] }]), ...provideHttpTesting()],
    });
    store = TestBed.inject(AuthStore);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    storage = TestBed.inject(TokenStorage);
    notify = TestBed.inject(Notify);
    vi.spyOn(notify, 'info').mockImplementation(() => undefined);
  });

  afterEach(() => {
    store.discard(); // cancels a pending expiry timer
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('starts signed out', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.session()).toBeNull();
    expect(store.user()).toBeNull();
    expect(store.role()).toBeNull();
    expect(store.token()).toBeNull();
    expect(store.hasValidSession()).toBe(false);
  });

  describe('login', () => {
    it('posts the credentials without a token and opens the session from the response', () => {
      const user = fixtureUser('CLAIMS_STAFF', { id: 2, fullName: 'Rajesh Kumar' });
      const response = authResponseFor(user);
      let result: unknown;

      store.login({ email: 'staff@chubb.com', password: 'Staff@123' }).subscribe((u) => (result = u));
      const request = http.expectOne('/api/auth/login');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ email: 'staff@chubb.com', password: 'Staff@123' });
      expect(request.request.headers.has('Authorization')).toBe(false);
      request.flush(response);

      expect(result).toEqual(user);
      expect(store.isAuthenticated()).toBe(true);
      expect(store.user()).toEqual(user);
      expect(store.role()).toBe('CLAIMS_STAFF');
      expect(store.token()).toBe(response.accessToken);
      expect(store.hasValidSession()).toBe(true);
    });

    it('keeps the token for a reload', () => {
      signInAs('CLAIMANT');

      expect(storage.read()).toBe(store.token());
    });

    it('stays signed out, and says why, when the credentials are wrong', () => {
      const navigate = vi.spyOn(router, 'navigate');
      let error: unknown;

      store.login({ email: 'a@b.com', password: 'wrong' }).subscribe({ error: (e: unknown) => (error = e) });
      http.expectOne('/api/auth/login').flush(
        { status: 401, message: 'Invalid email or password' },
        { status: 401, statusText: 'Unauthorized' },
      );

      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).message).toBe('Invalid email or password');
      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
      expect(navigate).not.toHaveBeenCalled(); // a wrong password is not an expired session
    });
  });

  describe('expiry', () => {
    beforeEach(() => vi.useFakeTimers());

    it('signs out 30 seconds before the token expires, tells the user, and goes to login', () => {
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      signInAs('CLAIMANT', {}, 3600);

      vi.advanceTimersByTime((3600 - 31) * 1000);
      expect(store.isAuthenticated()).toBe(true);

      vi.advanceTimersByTime(2000);
      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
      expect(notify.info).toHaveBeenCalledWith('Your session has ended. Please sign in again.');
      expect(navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: undefined });
    });

    it('reports an expired session as invalid even if the timer never fired (a sleeping laptop)', () => {
      signInAs('CLAIMANT', {}, 3600);

      vi.setSystemTime(Date.now() + 3601 * 1000);

      expect(store.isAuthenticated()).toBe(true); // the signal only changes when someone signs out ...
      expect(store.hasValidSession()).toBe(false); // ... but the clock-based check is always right
    });

    it('does not fire after sign-out', () => {
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
      signInAs('CLAIMANT', {}, 3600);
      store.signOut('user');
      navigate.mockClear();

      vi.advanceTimersByTime(2 * 3600 * 1000);

      expect(navigate).not.toHaveBeenCalled();
    });
  });

  describe('signOut', () => {
    beforeEach(() => signInAs('CLAIMANT'));

    it('by the user: clears everything and goes to a clean login page, without a message', () => {
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      store.signOut('user');

      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
      expect(notify.info).not.toHaveBeenCalled();
      expect(navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: undefined });
    });

    it('because the server refused the token: remembers the page they were on', async () => {
      await router.navigateByUrl('/staff/claims/CLM-2026-000001?tab=notes');
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      store.signOut('unauthorized');

      expect(navigate).toHaveBeenCalledWith(['/auth/login'], {
        queryParams: { returnUrl: '/staff/claims/CLM-2026-000001?tab=notes' },
      });
      expect(notify.info).toHaveBeenCalledOnce();
    });

    it('does not remember the login page itself or the bare root', async () => {
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      store.signOut('expired');
      expect(navigate).toHaveBeenLastCalledWith(['/auth/login'], { queryParams: undefined });

      signInAs('CLAIMANT');
      await router.navigateByUrl('/auth/register');
      store.signOut('expired');
      expect(navigate).toHaveBeenLastCalledWith(['/auth/login'], { queryParams: undefined });
    });

    it('acts once when several requests are refused together', async () => {
      await router.navigateByUrl('/claimant/my-claims');
      const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

      store.signOut('unauthorized');
      store.signOut('unauthorized');
      store.signOut('expired');

      expect(navigate).toHaveBeenCalledOnce();
      expect(notify.info).toHaveBeenCalledOnce();
    });
  });

  describe('discard', () => {
    it('drops the session silently', () => {
      const navigate = vi.spyOn(router, 'navigate');
      signInAs('MANAGER');

      store.discard();

      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
      expect(navigate).not.toHaveBeenCalled();
      expect(notify.info).not.toHaveBeenCalled();
    });
  });

  describe('restore', () => {
    it('does nothing when no token is stored', async () => {
      await store.restore();

      http.expectNone(() => true);
      expect(store.isAuthenticated()).toBe(false);
    });

    it.each([
      ['malformed', 'not-a-jwt'],
      ['expired', fakeJwt({ exp: Math.floor(Date.now() / 1000) - 60 })],
    ])('discards a %s token without asking the server', async (_label, token) => {
      storage.write(token);

      await store.restore();

      http.expectNone(() => true);
      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
    });

    it('resumes a stored token that auth-service still accepts, with the freshest profile', async () => {
      const token = fakeJwt({ sub: '7', role: 'CLAIMANT' });
      storage.write(token);

      const restored = store.restore();
      const request = http.expectOne('/api/users/me');
      expect(request.request.headers.get('Authorization')).toBe(`Bearer ${token}`);
      request.flush(fixtureUser('CLAIMANT', { fullName: 'Wei Ling Tan-Lim' }));
      await restored;

      expect(store.isAuthenticated()).toBe(true);
      expect(store.token()).toBe(token);
      expect(store.user()?.fullName).toBe('Wei Ling Tan-Lim');
      expect(store.hasValidSession()).toBe(true);
    });

    it('signs out quietly when the server no longer accepts the token (deleted user, restarted backend)', async () => {
      const navigate = vi.spyOn(router, 'navigate');
      storage.write(fakeJwt());

      const restored = store.restore();
      http.expectOne('/api/users/me').flush({ message: 'Authentication is required' }, { status: 401, statusText: 'Unauthorized' });
      await restored;

      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
      expect(navigate).not.toHaveBeenCalled(); // nobody is mid-task at start-up, so no redirect and no message
      expect(notify.info).not.toHaveBeenCalled();
    });

    it('signs out when auth-service cannot be reached', async () => {
      storage.write(fakeJwt());

      const restored = store.restore();
      http.expectOne('/api/users/me').error(new ProgressEvent('error'));
      await restored;

      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
    });

    it('gives up waiting for auth-service after five seconds, so start-up cannot hang', async () => {
      vi.useFakeTimers();
      storage.write(fakeJwt());

      const restored = store.restore();
      http.expectOne('/api/users/me'); // never answered
      await vi.advanceTimersByTimeAsync(5001);
      await restored;

      expect(store.isAuthenticated()).toBe(false);
      expect(storage.read()).toBeNull();
    });
  });
});
