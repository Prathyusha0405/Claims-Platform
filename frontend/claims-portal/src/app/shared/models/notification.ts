import { ClaimEventType } from './claim';

/** One inbox entry (claim-service `NotificationResponse`). Named to stay clear of the browser's `Notification`. */
export interface UserNotification {
  id: number;
  claimId: number;
  claimNumber: string;
  eventType: ClaimEventType;
  message: string;
  read: boolean;
  createdAt: string;
}

export interface UnreadCount {
  unreadCount: number;
}

export interface MarkedRead {
  markedRead: number;
}
