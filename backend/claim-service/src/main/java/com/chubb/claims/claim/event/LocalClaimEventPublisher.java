package com.chubb.claims.claim.event;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-process publisher, used unless {@code app.events.publisher=kafka}. It hands every event straight to the
 * {@link ClaimEventHandler} (log, audit row, notifications) and joins the caller's transaction, so the claim change, its
 * audit row and its notifications are committed (or rolled back) together.
 */
@Component
@ConditionalOnProperty(prefix = "app.events", name = "publisher", havingValue = "local", matchIfMissing = true)
public class LocalClaimEventPublisher implements ClaimEventPublisher {

    private final ClaimEventHandler handler;

    public LocalClaimEventPublisher(ClaimEventHandler handler) {
        this.handler = handler;
    }

    @Override
    @Transactional
    public void publish(ClaimEvent event) {
        handler.apply(event);
    }
}
