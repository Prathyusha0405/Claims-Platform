package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-process publisher. For every event it logs, appends an audit row to claim_history and creates the
 * notifications. It joins the caller's transaction, so the claim change, its audit row and its notifications
 * are committed (or rolled back) together.
 */
@Component
public class LocalClaimEventPublisher implements ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalClaimEventPublisher.class);

    private final ClaimHistoryRepository claimHistoryRepository;
    private final NotificationRepository notificationRepository;

    public LocalClaimEventPublisher(ClaimHistoryRepository claimHistoryRepository,
                                    NotificationRepository notificationRepository) {
        this.claimHistoryRepository = claimHistoryRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void publish(ClaimEvent event) {
        log.info("Claim event {} claim={} status={}->{} actor={}",
                event.type(), event.claimNumber(), event.fromStatus(), event.toStatus(), event.actorId());

        claimHistoryRepository.save(new ClaimHistory(
                event.claimId(),
                event.type(),
                event.fromStatus(),
                event.toStatus(),
                event.actorId(),
                event.actorRole(),
                event.details(),
                event.occurredAt()));

        notificationRepository.saveAll(ClaimNotificationFactory.notificationsFor(event));
    }
}
