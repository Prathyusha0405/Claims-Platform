import { HttpClient, HttpContext } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { HANDLE_UNAUTHORIZED } from '@core/interceptors/http-context';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '@core/models/auth';
import { API } from './endpoints';

/** auth-service over HTTP, nothing more. Session handling lives in AuthStore. */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(API.auth.login, request);
  }

  /** Creates a CLAIMANT account (201). 409 when the email is taken. */
  register(request: RegisterRequest): Observable<User> {
    return this.http.post<User>(API.auth.register, request);
  }

  /**
   * The signed-in user's profile. Pass `token` to check a token that is not the current session yet (a stored one
   * being restored); a 401 is then reported to the caller instead of signing anyone out.
   */
  me(token?: string): Observable<User> {
    return this.http.get<User>(API.auth.me, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      context: new HttpContext().set(HANDLE_UNAUTHORIZED, token === undefined),
    });
  }
}
