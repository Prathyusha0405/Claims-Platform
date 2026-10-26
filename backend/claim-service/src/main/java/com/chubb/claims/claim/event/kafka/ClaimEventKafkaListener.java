package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads claim events from the topic and records each one (audit row and notifications) through the same
 * {@link ClaimEventHandler} the local publisher uses. A duplicate delivery is ignored. A message that cannot be read is
 * not retried, since retrying cannot fix it; it goes straight to the dead-letter topic, as does an event that still cannot be
 * recorded after the configured retries (see {@code KafkaEventConfig}).
 */
@Component
@ConditionalOnProperty(prefix = "app.events", name = "publisher", havingValue = "kafka")
public class ClaimEventKafkaListener {

    private final ClaimEventHandler handler;
    private final ObjectMapper objectMapper;

    public ClaimEventKafkaListener(ClaimEventHandler handler, ObjectMapper objectMapper) {
        this.handler = handler;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.events.kafka.topic:claim-events}")
    public void onMessage(String payload) {
        handler.applyOnce(parse(payload));
    }

    private ClaimEvent parse(String payload) {
        try {
            return objectMapper.readValue(payload, ClaimEvent.class);
        } catch (JsonProcessingException | RuntimeException e) {
            throw new UnreadableClaimEventException(e);
        }
    }

    /** The message is not a claim event; retrying cannot help. */
    public static class UnreadableClaimEventException extends RuntimeException {

        public UnreadableClaimEventException(Throwable cause) {
            super("The message is not a readable claim event", cause);
        }
    }
}
