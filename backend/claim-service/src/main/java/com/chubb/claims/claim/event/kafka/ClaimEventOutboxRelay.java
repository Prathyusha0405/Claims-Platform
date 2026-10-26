package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.entity.ClaimEventOutbox;
import com.chubb.claims.claim.repository.ClaimEventOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Sends the events waiting in the outbox to Kafka, oldest first, and deletes each row once Kafka has acknowledged it.
 * <p>
 * Delivery is at least once: if the service stops after Kafka acknowledged an event but before its row is deleted, the event
 * is sent again, and the consumer ignores the duplicate. If Kafka cannot be reached the rows simply stay where they are and
 * the next run tries again, so a broker outage delays audit rows and notifications but never loses them. The relay stops at
 * the first failure so events are never sent out of order. The send and the delete are separate, short operations: no
 * database transaction is held open while waiting for the broker.
 * <p>
 * It assumes one running instance; two would not lose events but could send some twice, which the consumer tolerates.
 */
@Component
@ConditionalOnProperty(prefix = "app.events", name = "publisher", havingValue = "kafka")
public class ClaimEventOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventOutboxRelay.class);

    private final ClaimEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaEventProperties properties;

    public ClaimEventOutboxRelay(ClaimEventOutboxRepository outboxRepository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 KafkaEventProperties properties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.events.kafka.relay-delay-ms:500}")
    public void relay() {
        relayBatch();
    }

    /** Sends one batch and returns how many events Kafka acknowledged. */
    public int relayBatch() {
        List<ClaimEventOutbox> batch = outboxRepository.findAllByOrderByIdAsc(PageRequest.of(0, properties.batchSize()));
        if (batch.isEmpty()) {
            return 0;
        }

        List<Long> sent = new ArrayList<>(batch.size());
        try {
            for (ClaimEventOutbox row : batch) {
                kafkaTemplate.send(properties.topic(), String.valueOf(row.getClaimId()), row.getPayload())
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                sent.add(row.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while sending claim events to Kafka; {} sent, the rest stay in the outbox", sent.size());
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            log.warn("Could not send claim events to Kafka ({} of {} sent); the rest stay in the outbox and are retried: {}",
                    sent.size(), batch.size(), cause.toString());
        } finally {
            if (!sent.isEmpty()) {
                outboxRepository.deleteAllByIdInBatch(sent);
            }
        }
        return sent.size();
    }
}
