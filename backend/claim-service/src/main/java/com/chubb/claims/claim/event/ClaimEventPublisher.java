package com.chubb.claims.claim.event;

/**
 * Abstraction over how claim events leave the service layer. {@link LocalClaimEventPublisher} handles them in-process (the
 * default); {@code KafkaClaimEventPublisher} sends them through Kafka. The service layer only ever calls {@link #publish}, so
 * switching between them (with {@code app.events.publisher}) touches no business code.
 */
public interface ClaimEventPublisher {

    void publish(ClaimEvent event);
}
