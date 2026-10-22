import { inject, Injectable, signal } from '@angular/core';

import { NotificationApi } from './notification-api';

/**
 * The number on the toolbar's notification bell. One signal for the whole app: the bell keeps it fresh, and the
 * notifications page nudges it the moment something is read, so the badge never lags behind what the user just did.
 */
@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly api = inject(NotificationApi);
  private readonly count = signal(0);

  readonly unreadCount = this.count.asReadonly();

  /** Asks claim-service for the real number. A failed refresh keeps the old number: a badge is not worth an error. */
  refresh(): void {
    this.api.unreadCount().subscribe({
      next: (response) => this.count.set(response.unreadCount),
      error: () => undefined,
    });
  }

  /** Moves the badge by `delta` right away, before the next refresh confirms it. Never goes below zero. */
  adjust(delta: number): void {
    this.count.update((current) => Math.max(0, current + delta));
  }

  /** Forgets the number, e.g. when the signed-in user goes away. */
  reset(): void {
    this.count.set(0);
  }
}
