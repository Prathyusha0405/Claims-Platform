import { HttpClient, HttpContext } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { ApiError } from '@core/models/api-error';
import { AuthStore } from '@core/services/auth-store';
import { Notify } from '@core/services/notify';
import { provideHttpTesting, signInAs } from '@testing/auth-testing';
import { isApiRequest, isPublicApiRequest } from './api-request';
import { HANDLE_UNAUTHORIZED } from './http-context';

describe('API request classification', () => {
  it('treats only same-origin /api/ URLs as our backend', () => {
    expect(isApiRequest('/api/claims/search')).toBe(true);
    expect(isApiRequest(`${window.location.origin}/api/claims`)).toBe(true);

    expect(isApiRequest('https://example.com/api/claims')).toBe(false);
    expect(isApiRequest('//example.com/api/claims')).toBe(false);
    expect(isApiRequest('/assets/logo.svg')).toBe(false);
    expect(isApiRequest('/apix/claims')).toBe(false);
  });

  it('knows which calls are public', () => {
    expect(isPublicApiRequest('/api/auth/login')).toBe(true);
    expect(isPublicApiRequest('/api/auth/register')).toBe(true);
    expect(isPublicApiRequest('/api/auth/login?x=1')).toBe(true);

    expect(isPublicApiRequest('/api/users/me')).toBe(false);
    expect(isPublicApiRequest('/api/claims')).toBe(false);
  });
});

describe('HTTP interceptors', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let store: AuthStore;
  let notify: Notify;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), ...provideHttpTesting()] });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
    notify = TestBed.inject(Notify);
    router = TestBed.inject(Router);
    vi.spyOn(notify, 'info').mockImplementation(() => undefined);
    vi.spyOn(notify, 'error').mockImplementation(() => undefined);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    store.discard();
    vi.restoreAllMocks();
  });

  describe('authInterceptor', () => {
    it('adds the bearer token to calls to our backend while signed in', () => {
      signInAs('CLAIMANT');

      client.get('/api/claims/my-claims').subscribe();

      expect(http.expectOne('/api/claims/my-claims').request.headers.get('Authorization')).toBe(`Bearer ${store.token()}`);
    });

    it('sends nothing while signed out', () => {
      client.get('/api/claims/my-claims').subscribe();

      expect(http.expectOne('/api/claims/my-claims').request.headers.has('Authorization')).toBe(false);
    });

    it.each(['/api/auth/login', '/api/auth/register'])('never sends the token to %s', (url) => {
      signInAs('CLAIMANT');

      client.post(url, {}).subscribe();

      expect(http.expectOne(url).request.headers.has('Authorization')).toBe(false);
    });

    it('never leaks the token to another origin or a non-API path', () => {
      signInAs('CLAIMANT');

      client.get('https://example.com/api/claims').subscribe();
      client.get('/assets/config.json').subscribe();

      expect(http.expectOne('https://example.com/api/claims').request.headers.has('Authorization')).toBe(false);
      expect(http.expectOne('/assets/config.json').request.headers.has('Authorization')).toBe(false);
    });

    it('leaves a header the caller set explicitly alone', () => {
      signInAs('CLAIMANT');

      client.get('/api/users/me', { headers: { Authorization: 'Bearer someone-elses-token' } }).subscribe();

      expect(http.expectOne('/api/users/me').request.headers.get('Authorization')).toBe('Bearer someone-elses-token');
    });
  });

  describe('errorInterceptor', () => {
    it('hands components an ApiError built from the backend error JSON', () => {
      let error: unknown;
      client.post('/api/claims', {}).subscribe({ error: (e: unknown) => (error = e) });

      http.expectOne('/api/claims').flush(
        { status: 400, message: 'Validation failed', path: '/api/claims', fieldErrors: { policyNumber: 'must not be blank' } },
        { status: 400, statusText: 'Bad Request' },
      );

      expect(error).toBeInstanceOf(ApiError);
      expect((error as ApiError).status).toBe(400);
      expect((error as ApiError).message).toBe('Validation failed');
      expect((error as ApiError).fieldErrors).toEqual({ policyNumber: 'must not be blank' });
    });

    it('reports a dead network as an ApiError with status 0', () => {
      let error: unknown;
      client.get('/api/claims/search').subscribe({ error: (e: unknown) => (error = e) });

      http.expectOne('/api/claims/search').error(new ProgressEvent('error'));

      expect((error as ApiError).isNetwork).toBe(true);
    });

    it('passes 404, 409 and 503 on for the page to show in context, without signing out or a toast', () => {
      signInAs('CLAIMS_STAFF');
      const errors: ApiError[] = [];
      for (const url of ['/api/claims/CLM-1', '/api/staff/claims/1/assign', '/api/dashboard/status-summary']) {
        client.get(url).subscribe({ error: (e: ApiError) => errors.push(e) });
      }

      http.expectOne('/api/claims/CLM-1').flush({ message: 'not found' }, { status: 404, statusText: 'Not Found' });
      http.expectOne('/api/staff/claims/1/assign').flush({ message: 'taken' }, { status: 409, statusText: 'Conflict' });
      http.expectOne('/api/dashboard/status-summary').flush(null, { status: 503, statusText: 'Service Unavailable' });

      expect(errors.map((e) => e.status)).toEqual([404, 409, 503]);
      expect(errors[2].isUnavailable).toBe(true);
      expect(store.isAuthenticated()).toBe(true);
      expect(notify.error).not.toHaveBeenCalled();
    });

    it('signs out on a 401 from a protected call, and does it once for a burst of them', async () => {
      signInAs('CLAIMANT');
      await router.navigateByUrl('/claimant/my-claims');
      client.get('/api/claims/my-claims').subscribe({ error: () => undefined });
      client.get('/api/notifications/unread-count').subscribe({ error: () => undefined });

      http.expectOne('/api/claims/my-claims').flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });
      http.expectOne('/api/notifications/unread-count').flush({ message: 'expired' }, { status: 401, statusText: 'Unauthorized' });

      expect(store.isAuthenticated()).toBe(false);
      expect(router.navigate).toHaveBeenCalledOnce();
      expect(router.navigate).toHaveBeenCalledWith(['/auth/login'], { queryParams: { returnUrl: '/claimant/my-claims' } });
      expect(notify.info).toHaveBeenCalledOnce();
    });

    it('leaves a 401 from sign-in alone: it only means the credentials were wrong', () => {
      const signOut = vi.spyOn(store, 'signOut');
      let error: unknown;
      client.post('/api/auth/login', {}).subscribe({ error: (e: unknown) => (error = e) });

      http.expectOne('/api/auth/login').flush({ message: 'Invalid email or password' }, { status: 401, statusText: 'Unauthorized' });

      expect(signOut).not.toHaveBeenCalled();
      expect((error as ApiError).isUnauthorized).toBe(true);
    });

    it('leaves a 401 alone when the caller asked to handle it', () => {
      signInAs('CLAIMANT');
      const signOut = vi.spyOn(store, 'signOut');
      const context = new HttpContext().set(HANDLE_UNAUTHORIZED, false);
      client.get('/api/users/me', { context }).subscribe({ error: () => undefined });

      http.expectOne('/api/users/me').flush({ message: 'nope' }, { status: 401, statusText: 'Unauthorized' });

      expect(signOut).not.toHaveBeenCalled();
      expect(store.isAuthenticated()).toBe(true);
    });

    it('tells the user about a 403 and keeps them where they are', () => {
      signInAs('CLAIMANT');
      let error: unknown;
      client.post('/api/staff/claims/1/approve', {}).subscribe({ error: (e: unknown) => (error = e) });

      http.expectOne('/api/staff/claims/1/approve').flush({ message: 'Forbidden' }, { status: 403, statusText: 'Forbidden' });

      expect(notify.error).toHaveBeenCalledWith('You do not have permission to do that.');
      expect((error as ApiError).isForbidden).toBe(true);
      expect(store.isAuthenticated()).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('lets successful responses through untouched', () => {
      let body: unknown;
      client.get('/api/claims/search').subscribe((b) => (body = b));

      http.expectOne('/api/claims/search').flush({ content: [] });

      expect(body).toEqual({ content: [] });
    });
  });
});
