package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * What happens to a claim event, however it arrived: it is logged, an audit row is appended to claim_history and the
 * notifications are created. {@link LocalClaimEventPublisher} calls it in the caller's transaction; the Kafka listener calls
 * it for each message it receives.
 */
@Component
public class ClaimEventHandler {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventHandler.class);

    private final ClaimHistoryRepository claimHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final ClaimsStaffDirectory claimsStaffDirectory;

    public ClaimEventHandler(ClaimHistoryRepository claimHistoryRepository,
                             NotificationRepository notificationRepository,
                             ClaimsStaffDirectory claimsStaffDirectory) {
        this.claimHistoryRepository = claimHistoryRepository;
        this.notificationRepository = notificationRepository;
        this.claimsStaffDirectory = claimsStaffDirectory;
    }

    /** Records the event. Joins the caller's transaction, so the audit row and notifications commit with it. */
    @Transactional
    public void apply(ClaimEvent event) {
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
                event.occurredAt(),
                event.id()));

        List<Long> claimsStaffIds = event.type() == ClaimEventType.CLAIM_CREATED
                ? claimsStaffDirectory.claimsStaffIds()
                : List.of();
        notificationRepository.saveAll(ClaimNotificationFactory.notificationsFor(event, claimsStaffIds));
    }

    /**
     * Records the event unless it has been recorded already. A broker delivers at least once, so the same event can arrive
     * twice; the audit row and the notifications are written together, so a recorded event is never half done.
     *
     * @return true if the event was recorded now, false if it was a duplicate
     */
    @Transactional
    public boolean applyOnce(ClaimEvent event) {
        if (claimHistoryRepository.existsByEventId(event.id())) {
            log.info("Claim event {} claim={} was already recorded; ignoring the duplicate", event.type(), event.claimNumber());
            return false;
        }
        apply(event);
        return true;
    }
}
