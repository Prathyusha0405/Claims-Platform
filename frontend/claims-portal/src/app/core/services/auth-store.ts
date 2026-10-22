import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, map, Observable, tap, timeout } from 'rxjs';

import { LoginRequest, Session, User } from '@core/models/auth';
import { Role } from '@core/models/role';
import { AuthApi } from './auth-api';
import { decodeJwt } from './jwt';
import { Notify } from './notify';
import { TokenStorage } from './token-storage';

/** The session is ended this long before the token really expires, so no request is sent with a dying token. */
const EXPIRY_MARGIN_MS = 30_000;
/** How long start-up waits for auth-service to confirm a stored token before giving up and asking for a sign-in. */
const RESTORE_TIMEOUT_MS = 5_000;
/** `setTimeout` cannot wait longer than a signed 32-bit number of milliseconds. */
const MAX_TIMER_MS = 2 ** 31 - 1;

/** Why a session ended: the user chose to, the token ran out, or the server refused it. */
export type SignOutReason = 'user' | 'expired' | 'unauthorized';

/**
 * Who is signed in, for the whole app. The session is a signal, so the shell and guards react to it; the token is
 * kept in `sessionStorage` (via TokenStorage) so a reload does not sign the user out.
 *
 * Nothing here is a security boundary: the backend validates the token on every call. This store only decides what
 * the UI shows and where the router sends people.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly storage = inject(TokenStorage);
  private readonly router = inject(Router);
  private readonly notify = inject(Notify);

  private readonly state = signal<Session | null>(null);
  private expiryTimer: ReturnType<typeof setTimeout> | undefined;

  readonly session = this.state.asReadonly();
  readonly user = computed(() => this.state()?.user ?? null);
  readonly role = computed<Role | null>(() => this.state()?.user.role ?? null);
  readonly token = computed(() => this.state()?.token ?? null);
  readonly isAuthenticated = computed(() => this.state() !== null);

  /**
   * True while there is a session whose token has not expired. Unlike `isAuthenticated` this reads the clock, so
   * guards stay correct even if the browser slept through the expiry timer.
   */
  hasValidSession(): boolean {
    const session = this.state();
    return session !== null && session.expiresAt > Date.now();
  }

  /** Signs in with email and password; the session starts when the call succeeds. */
  login(credentials: LoginRequest): Observable<User> {
    return this.api.login(credentials).pipe(
      tap((response) => this.open(response.accessToken, response.user, Date.now() + response.expiresIn * 1000)),
      map((response) => response.user),
    );
  }

  /**
   * Called once at start-up, before the first page renders. Resumes the session kept in `sessionStorage` if its
   * token is still valid AND auth-service still knows the user. Any other outcome (no token, expired, malformed,
   * rejected, user deleted, service unreachable) leaves the app signed out, which is always safe.
   */
  async restore(): Promise<void> {
    const token = this.storage.read();
    if (token === null) {
      return;
    }

    const payload = decodeJwt(token);
    const expiresAt = payload === null ? 0 : payload.exp * 1000;
    if (expiresAt <= Date.now()) {
      this.storage.clear();
      return;
    }

    try {
      const user = await firstValueFrom(this.api.me(token).pipe(timeout(RESTORE_TIMEOUT_MS)));
      this.open(token, user, expiresAt);
    } catch {
      this.storage.clear();
    }
  }

  /**
   * Ends the session and goes to the login page. When the cause is not the user's own choice the page they were on
   * is remembered (`returnUrl`) and a message explains what happened. Calling it again while already signed out
   * does nothing, so a burst of parallel 401 responses signs out and redirects exactly once.
   */
  signOut(reason: SignOutReason = 'user'): void {
    const wasSignedIn = this.state() !== null;
    this.discard();
    if (!wasSignedIn && reason !== 'user') {
      return;
    }

    const returnUrl = reason === 'user' ? null : this.currentAppUrl();
    if (reason !== 'user') {
      this.notify.info('Your session has ended. Please sign in again.');
    }
    void this.router.navigate(['/auth/login'], { queryParams: returnUrl === null ? undefined : { returnUrl } });
  }

  /** Forgets the session without navigating; for guards that are about to redirect themselves. */
  discard(): void {
    clearTimeout(this.expiryTimer);
    this.expiryTimer = undefined;
    this.storage.clear();
    this.state.set(null);
  }

  private open(token: string, user: User, expiresAt: number): void {
    this.storage.write(token);
    this.state.set({ token, user, expiresAt });
    this.scheduleExpiry(expiresAt);
  }

  private scheduleExpiry(expiresAt: number): void {
    clearTimeout(this.expiryTimer);
    const endsAt = expiresAt - EXPIRY_MARGIN_MS;
    this.expiryTimer = setTimeout(
      () => (Date.now() >= endsAt ? this.signOut('expired') : this.scheduleExpiry(expiresAt)),
      Math.min(Math.max(endsAt - Date.now(), 0), MAX_TIMER_MS),
    );
  }

  /** The page to come back to after signing in again, or null when the user was not on a protected page. */
  private currentAppUrl(): string | null {
    const url = this.router.url;
    return url === '/' || url.startsWith('/auth') ? null : url;
  }
}
