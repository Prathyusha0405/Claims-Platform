import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PageRequest } from '@shared/models/page';
import { MarkedRead, UnreadCount, UserNotification } from '@shared/models/notification';
import { API } from './endpoints';
import { pageParams, toParams } from './http-params';

/** claim-service's notification inbox. Everything is strictly the caller's own. */
@Injectable({ providedIn: 'root' })
export class NotificationApi {
  private readonly http = inject(HttpClient);

  /** Newest first. `createdAt` is the only field the backend lets you sort by. */
  list(unreadOnly: boolean, page: PageRequest): Observable<Page<UserNotification>> {
    return this.http.get<Page<UserNotification>>(API.notifications.list, {
      params: toParams({ unreadOnly, ...pageParams(page) }),
    });
  }

  unreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(API.notifications.unreadCount);
  }

  /** Idempotent. 404 for somebody else's notification. */
  markRead(id: number): Observable<UserNotification> {
    return this.http.post<UserNotification>(API.notifications.markRead(id), {});
  }

  markAllRead(): Observable<MarkedRead> {
    return this.http.post<MarkedRead>(API.notifications.readAll, {});
  }
}
