package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.NotificationResponse;
import com.chubb.claims.claim.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Notifications are stored against a claim id; the inbox shows the claim number, so every read joins for it. */
    String INBOX_ITEM = "select new com.chubb.claims.claim.dto.NotificationResponse("
            + "n.id, n.claimId, c.claimNumber, n.eventType, n.message, n.read, n.createdAt) "
            + "from Notification n join Claim c on c.id = n.claimId ";

    Page<Notification> findByRecipientId(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadFalse(Long recipientId);

    /** The recipient's inbox; sorting comes from the pageable. */
    @Query(value = INBOX_ITEM + "where n.recipientId = :recipientId and (:unreadOnly = false or n.read = false)",
            countQuery = "select count(n) from Notification n "
                    + "where n.recipientId = :recipientId and (:unreadOnly = false or n.read = false)")
    Page<NotificationResponse> findInbox(@Param("recipientId") Long recipientId,
                                         @Param("unreadOnly") boolean unreadOnly,
                                         Pageable pageable);

    /** Empty when the notification does not exist or belongs to someone else. */
    @Query(INBOX_ITEM + "where n.id = :id and n.recipientId = :recipientId")
    Optional<NotificationResponse> findInboxItem(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** Only ever touches the recipient's own, still-unread row; returns how many rows changed (0 or 1). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.id = :id and n.recipientId = :recipientId and n.read = false")
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** One statement for the whole inbox; returns how many rows were unread. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markAllRead(@Param("recipientId") Long recipientId);
}
