import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EnvironmentProviders, Provider } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { authInterceptor } from '@core/interceptors/auth-interceptor';
import { errorInterceptor } from '@core/interceptors/error-interceptor';
import { AuthResponse, User } from '@core/models/auth';
import { Role } from '@core/models/role';
import { AuthStore } from '@core/services/auth-store';
import { JwtPayload } from '@core/services/jwt';

/** Test helpers only (never imported by app code): fake tokens and signing a user in through the real AuthStore. */

/** base64url of UTF-8 text, as it appears in a JWT segment. */
export function base64Url(text: string): string {
  const binary = String.fromCharCode(...new TextEncoder().encode(text));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** An unsigned token shaped like the ones auth-service issues (the app only decodes, it never verifies). */
export function fakeJwt(claims: Partial<JwtPayload> = {}): string {
  const nowSeconds = Math.floor(Date.now() / 1000);
  const payload: JwtPayload = {
    sub: '7',
    email: 'weiling.tan@example.com',
    name: 'Wei Ling Tan',
    role: 'CLAIMANT',
    iat: nowSeconds,
    exp: nowSeconds + 3600,
    ...claims,
  };
  return [base64Url('{"alg":"HS256","typ":"JWT"}'), base64Url(JSON.stringify(payload)), 'signature'].join('.');
}

export function fixtureUser(role: Role = 'CLAIMANT', overrides: Partial<User> = {}): User {
  return {
    id: 7,
    fullName: 'Wei Ling Tan',
    email: 'weiling.tan@example.com',
    role,
    createdAt: '2026-10-01T00:00:00Z',
    ...overrides,
  };
}

/** What `POST /api/auth/login` answers for this user. */
export function authResponseFor(user: User, expiresInSeconds = 3600): AuthResponse {
  const nowSeconds = Math.floor(Date.now() / 1000);
  return {
    accessToken: fakeJwt({
      sub: String(user.id),
      email: user.email,
      name: user.fullName,
      role: user.role,
      iat: nowSeconds,
      exp: nowSeconds + expiresInSeconds,
    }),
    tokenType: 'Bearer',
    expiresIn: expiresInSeconds,
    user,
  };
}

/** HttpClient wired as in the app (both interceptors) but backed by the testing controller. */
export function provideHttpTesting(): (Provider | EnvironmentProviders)[] {
  return [provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])), provideHttpClientTesting()];
}

/** Signs a user in through the real `AuthStore.login`; needs `provideHttpTesting()` in the TestBed. */
export function signInAs(role: Role = 'CLAIMANT', overrides: Partial<User> = {}, expiresInSeconds = 3600): User {
  const user = fixtureUser(role, overrides);
  TestBed.inject(AuthStore).login({ email: user.email, password: 'Secret#123' }).subscribe();
  TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush(authResponseFor(user, expiresInSeconds));
  return user;
}
