package com.chubb.claims.claim.event;

/**
 * Abstraction over how claim events leave the service layer. {@link LocalClaimEventPublisher} handles them
 * in-process today; a broker-backed implementation can replace it without touching the service layer.
 */
public interface ClaimEventPublisher {

    void publish(ClaimEvent event);
}
