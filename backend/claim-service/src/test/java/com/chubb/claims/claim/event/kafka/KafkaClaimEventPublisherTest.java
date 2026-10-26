package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.entity.ClaimEventOutbox;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.event.ClaimEvent;
import com.chubb.claims.claim.repository.ClaimEventOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaClaimEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Mock
    private ClaimEventOutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void publishSavesTheEventToTheOutboxAsJson() throws Exception {
        KafkaClaimEventPublisher publisher = new KafkaClaimEventPublisher(outboxRepository, objectMapper);
        ClaimEvent event = event();

        publisher.publish(event);

        ArgumentCaptor<ClaimEventOutbox> saved = ArgumentCaptor.forClass(ClaimEventOutbox.class);
        verify(outboxRepository).save(saved.capture());
        ClaimEventOutbox row = saved.getValue();
        assertThat(row.getClaimId()).isEqualTo(10L);
        assertThat(row.getEventId()).isEqualTo(event.id());
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
        assertThat(objectMapper.readValue(row.getPayload(), ClaimEvent.class)).isEqualTo(event);
    }

    @Test
    void anEventThatCannotBeWrittenAsJsonFailsTheClaimChangeInsteadOfBeingLost() throws Exception {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(event())).thenThrow(new JsonProcessingException("boom") { });
        KafkaClaimEventPublisher publisher = new KafkaClaimEventPublisher(outboxRepository, failing);

        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLAIM_APPROVED")
                .hasMessageContaining("CLM-2026-000001");
        verifyNoInteractions(outboxRepository);
    }

    private static ClaimEvent event() {
        return new ClaimEvent(ClaimEventType.CLAIM_APPROVED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 7L, "CLAIMS_STAFF", "Claim approved", NOW);
    }
}
