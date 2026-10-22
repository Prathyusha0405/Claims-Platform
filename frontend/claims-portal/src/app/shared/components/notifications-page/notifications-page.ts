import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleChange, MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AuthStore } from '@core/services/auth-store';
import { NotificationApi } from '@core/services/notification-api';
import { NotificationStore } from '@core/services/notification-store';
import { Notify } from '@core/services/notify';
import { LoadError } from '@shared/components/load-error/load-error';
import { CLAIM_EVENT_ICONS } from '@shared/models/claim';
import { UserNotification } from '@shared/models/notification';
import { PageRequest } from '@shared/models/page';
import { loader } from '@shared/utils/loader';

type Filter = 'all' | 'unread';

const PAGE_SIZES = [10, 20, 50];

/**
 * The signed-in user's notification inbox, shared by claimants and officers. Each entry links to its claim in the
 * user's own area; opening one marks it read.
 */
@Component({
  imports: [
    DatePipe,
    LoadError,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressBarModule,
    RouterLink,
  ],
  selector: 'app-notifications-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './notifications-page.scss',
  templateUrl: './notifications-page.html',
})
export class NotificationsPage {
  private readonly api = inject(NotificationApi);
  private readonly store = inject(NotificationStore);
  private readonly notify = inject(Notify);
  private readonly auth = inject(AuthStore);

  protected readonly filter = signal<Filter>('all');
  protected readonly request = signal<PageRequest>({ page: 0, size: 20, sortField: 'createdAt', sortDirection: 'desc' });
  private readonly reloads = signal(0);

  protected readonly inbox = loader(
    computed(() => ({ unreadOnly: this.filter() === 'unread', request: this.request(), reload: this.reloads() })),
    ({ unreadOnly, request }) => this.api.list(unreadOnly, request),
  );
  protected readonly unreadCount = this.store.unreadCount;
  /** Officers review claims in their own area; claimants track them in theirs. */
  protected readonly claimBase = computed(() => (this.auth.role() === 'OFFICER' ? '/officer/claims' : '/claimant/claims'));
  protected readonly icons = CLAIM_EVENT_ICONS;
  protected readonly pageSizes = PAGE_SIZES;

  constructor() {
    this.store.refresh();
  }

  protected setFilter(change: MatButtonToggleChange): void {
    this.filter.set(change.value as Filter);
    this.request.update((current) => ({ ...current, page: 0 }));
  }

  protected turnPage(event: PageEvent): void {
    this.request.update((current) => ({ ...current, page: event.pageIndex, size: event.pageSize }));
  }

  protected reload(): void {
    this.reloads.update((count) => count + 1);
  }

  /** Called as the link is followed, so the page the user is leaving does not wait for the server. */
  protected markRead(notification: UserNotification): void {
    if (notification.read) {
      return;
    }
    this.store.adjust(-1);
    this.api.markRead(notification.id).subscribe({ error: () => this.store.refresh() });
  }

  protected markAllRead(): void {
    this.api.markAllRead().subscribe({
      next: ({ markedRead }) => {
        this.notify.success(markedRead === 0 ? 'Nothing to mark.' : `Marked ${markedRead} as read.`);
        this.store.refresh();
        this.reload();
      },
      error: () => this.notify.error('Could not mark your notifications as read. Please try again.'),
    });
  }
}
