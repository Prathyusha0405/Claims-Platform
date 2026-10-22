import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { timer } from 'rxjs';

import { AuthStore } from '@core/services/auth-store';
import { NotificationStore } from '@core/services/notification-store';

/** How often the badge asks claim-service for the unread count while the app is open. */
const POLL_MS = 60_000;

/**
 * The bell in the toolbar: a link to the inbox with the unread count on it. Only claimants and officers have an
 * inbox (claim-service never notifies admins), so for an admin it renders nothing and asks for nothing.
 */
@Component({
  imports: [MatBadgeModule, MatButtonModule, MatIconModule, RouterLink],
  selector: 'app-notification-bell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './notification-bell.html',
})
export class NotificationBell {
  private readonly auth = inject(AuthStore);
  private readonly store = inject(NotificationStore);

  protected readonly link = computed(() => {
    switch (this.auth.role()) {
      case 'CLAIMANT':
        return '/claimant/notifications';
      case 'OFFICER':
        return '/officer/notifications';
      default:
        return null;
    }
  });
  protected readonly count = this.store.unreadCount;
  protected readonly badge = computed(() => (this.count() > 99 ? '99+' : String(this.count())));
  protected readonly label = computed(() =>
    this.count() === 0 ? 'Notifications' : `Notifications, ${this.count()} unread`,
  );

  constructor() {
    if (this.link() === null) {
      return;
    }
    const destroyRef = inject(DestroyRef);
    timer(0, POLL_MS)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.store.refresh());
    // Whoever signs in next must not see this user's number while their own is on its way.
    destroyRef.onDestroy(() => this.store.reset());
  }
}
