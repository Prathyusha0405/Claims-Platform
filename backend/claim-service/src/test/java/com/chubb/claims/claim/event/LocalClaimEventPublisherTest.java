package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LocalClaimEventPublisherTest {

    @Mock
    private ClaimEventHandler handler;

    @InjectMocks
    private LocalClaimEventPublisher publisher;

    /** What a local publish does (audit row, notifications) is in ClaimEventHandlerTest; here it is only handed over. */
    @Test
    void publishHandsTheEventStraightToTheHandler() {
        ClaimEvent event = new ClaimEvent(ClaimEventType.CLAIM_CREATED, 10L, "CLM-2026-000001", 5L, null,
                null, ClaimStatus.SUBMITTED, 5L, "CLAIMANT", "Claim submitted", Instant.parse("2026-10-22T06:00:00Z"));

        publisher.publish(event);

        verify(handler).apply(event);
        verifyNoMoreInteractions(handler);
    }
}
