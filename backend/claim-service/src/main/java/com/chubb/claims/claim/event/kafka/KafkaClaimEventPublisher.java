package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.entity.ClaimEventOutbox;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventPublisher;
import com.chubb.claims.claim.repository.ClaimEventOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publisher for {@code app.events.publisher=kafka}. It does not talk to Kafka itself: it saves the event to the outbox table
 * inside the caller's transaction, so the event exists exactly when the claim change does (a rollback discards both, and a
 * broker outage cannot lose or block a claim change). {@link ClaimEventOutboxRelay} sends it from there.
 */
@Component
@ConditionalOnProperty(prefix = "app.events", name = "publisher", havingValue = "kafka")
public class KafkaClaimEventPublisher implements ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaClaimEventPublisher.class);

    private final ClaimEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KafkaClaimEventPublisher(ClaimEventOutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /** MANDATORY: an event staged outside the claim's own transaction would defeat the point of the outbox. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ClaimEvent event) {
        outboxRepository.save(new ClaimEventOutbox(event.claimId(), event.id(), toJson(event), event.occurredAt()));
        log.info("Claim event {} claim={} staged for Kafka", event.type(), event.claimNumber());
    }

    private String toJson(ClaimEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Claim event " + event.type() + " for claim " + event.claimNumber()
                    + " cannot be written as JSON", e);
        }
    }
}
