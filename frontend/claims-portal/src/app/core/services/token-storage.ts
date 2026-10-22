import { Injectable } from '@angular/core';

const KEY = 'chubb-claims.access-token';

/**
 * Where the access token lives between page loads. `sessionStorage` is tab-scoped: it survives a reload, is cleared
 * when the tab closes, and is not shared with other tabs (a second tab signs in on its own). Everything that touches
 * browser storage is in this class, so moving to another store is a one-file change.
 *
 * Storage can be missing or throw (private windows, blocked site data), so every access is guarded; the app then
 * simply behaves as "not signed in after a reload".
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  read(): string | null {
    try {
      return sessionStorage.getItem(KEY);
    } catch {
      return null;
    }
  }

  write(token: string): void {
    try {
      sessionStorage.setItem(KEY, token);
    } catch {
      // Not persisted; the session lasts until the page is reloaded.
    }
  }

  clear(): void {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      // Nothing to clear.
    }
  }
}
