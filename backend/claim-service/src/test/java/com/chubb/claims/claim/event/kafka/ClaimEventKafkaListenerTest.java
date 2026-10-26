package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.event.ClaimEventHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimEventKafkaListenerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Mock
    private ClaimEventHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ClaimEventKafkaListener listener() {
        return new ClaimEventKafkaListener(handler, objectMapper);
    }

    @Test
    void aMessageIsReadBackIntoTheEventAndRecordedOnce() throws Exception {
        ClaimEvent event = event();

        listener().onMessage(objectMapper.writeValueAsString(event));

        verify(handler).applyOnce(event);
    }

    @Test
    void aMessageThatIsNotAClaimEventIsReportedAsUnreadableAndNotRecorded() {
        assertThatThrownBy(() -> listener().onMessage("this is not json"))
                .isInstanceOf(ClaimEventKafkaListener.UnreadableClaimEventException.class);
        assertThatThrownBy(() -> listener().onMessage("{\"type\":\"NOT_AN_EVENT\"}"))
                .isInstanceOf(ClaimEventKafkaListener.UnreadableClaimEventException.class);

        verifyNoInteractions(handler);
    }

    @Test
    void aFailureToRecordTheEventReachesTheErrorHandlerSoItCanBeRetriedAndParked() throws Exception {
        ClaimEvent event = event();
        when(handler.applyOnce(event)).thenThrow(new DataIntegrityViolationException("claim does not exist"));

        assertThatThrownBy(() -> listener().onMessage(objectMapper.writeValueAsString(event)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static ClaimEvent event() {
        return new ClaimEvent(ClaimEventType.CLAIM_APPROVED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 7L, "CLAIMS_STAFF", "Claim approved", NOW);
    }
}
