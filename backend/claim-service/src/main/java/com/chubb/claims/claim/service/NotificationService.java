package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.MarkedReadResponse;
import com.chubb.claims.claim.dto.NotificationResponse;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.dto.UnreadCountResponse;
import com.chubb.claims.claim.exception.NotificationNotFoundException;
import com.chubb.claims.claim.repository.NotificationRepository;
import com.chubb.claims.claim.security.AuthenticatedUser;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * The reading side of notifications: the inbox, the unread badge and "mark as read". Notifications are raised by the
 * event publisher; every method here is scoped to the caller, so nobody can see or touch another user's inbox.
 */
@Service
public class NotificationService {

    /** An inbox is only ever ordered by when its entries were raised (newest first unless the client asks otherwise). */
    static final Set<String> SORTABLE_FIELDS = Set.of("createdAt");

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getNotifications(AuthenticatedUser user, boolean unreadOnly, Pageable pageable) {
        return PageResponse.from(notificationRepository.findInbox(
                user.userId(), unreadOnly, PageableSanitizer.sanitize(pageable, SORTABLE_FIELDS)));
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse countUnread(AuthenticatedUser user) {
        return new UnreadCountResponse(notificationRepository.countByRecipientIdAndReadFalse(user.userId()));
    }

    /** Idempotent: marking an already-read notification returns it unchanged. */
    @Transactional
    public NotificationResponse markRead(Long id, AuthenticatedUser user) {
        notificationRepository.markRead(id, user.userId());
        return notificationRepository.findInboxItem(id, user.userId())
                .orElseThrow(() -> new NotificationNotFoundException(id));
    }

    @Transactional
    public MarkedReadResponse markAllRead(AuthenticatedUser user) {
        return new MarkedReadResponse(notificationRepository.markAllRead(user.userId()));
    }
}
