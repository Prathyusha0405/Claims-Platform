package com.chubb.claims.claim.event.kafka;

import com.chubb.claims.claim.entity.ClaimEventOutbox;
import com.chubb.claims.claim.repository.ClaimEventOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimEventOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Mock
    private ClaimEventOutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final KafkaEventProperties properties = new KafkaEventProperties("claim-events", null, null, 2,
            Duration.ofMillis(100), null, null);

    private ClaimEventOutboxRelay relay() {
        return new ClaimEventOutboxRelay(outboxRepository, kafkaTemplate, properties);
    }

    @Test
    void sendsTheOldestEventsFirstKeyedByClaimAndThenDeletesThem() {
        when(outboxRepository.findAllByOrderByIdAsc(PageRequest.of(0, 2)))
                .thenReturn(List.of(row(1L, 10L, "{one}"), row(2L, 11L, "{two}")));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThat(relay().relayBatch()).isEqualTo(2);

        var order = inOrder(kafkaTemplate, outboxRepository);
        order.verify(kafkaTemplate).send("claim-events", "10", "{one}");
        order.verify(kafkaTemplate).send("claim-events", "11", "{two}");
        order.verify(outboxRepository).deleteAllByIdInBatch(List.of(1L, 2L));
    }

    @Test
    void doesNothingWhenTheOutboxIsEmpty() {
        when(outboxRepository.findAllByOrderByIdAsc(any())).thenReturn(List.of());

        assertThat(relay().relayBatch()).isZero();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void whenKafkaRejectsAnEventItStopsThereAndOnlyDeletesWhatWasSent() {
        when(outboxRepository.findAllByOrderByIdAsc(any()))
                .thenReturn(List.of(row(1L, 10L, "{one}"), row(2L, 11L, "{two}")));
        when(kafkaTemplate.send("claim-events", "10", "{one}")).thenReturn(CompletableFuture.completedFuture(null));
        when(kafkaTemplate.send("claim-events", "11", "{two}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

        assertThat(relay().relayBatch()).isEqualTo(1);

        verify(outboxRepository).deleteAllByIdInBatch(List.of(1L));
    }

    @Test
    void whenKafkaCannotBeReachedNothingIsDeletedAndNothingIsThrown() {
        when(outboxRepository.findAllByOrderByIdAsc(any())).thenReturn(List.of(row(1L, 10L, "{one}")));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenThrow(new IllegalStateException("Topic claim-events not present in metadata after 5000 ms"));

        assertThat(relay().relayBatch()).isZero();

        verify(outboxRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void anEventKafkaNeverAcknowledgesIsLeftInTheOutboxAfterTheTimeout() {
        when(outboxRepository.findAllByOrderByIdAsc(any())).thenReturn(List.of(row(1L, 10L, "{one}")));
        when(kafkaTemplate.send(any(String.class), any(String.class), any(String.class)))
                .thenReturn(new CompletableFuture<>());

        assertThat(relay().relayBatch()).isZero();

        verify(outboxRepository, never()).deleteAllByIdInBatch(anyList());
    }

    private static ClaimEventOutbox row(long id, long claimId, String payload) {
        ClaimEventOutbox row = new ClaimEventOutbox(claimId, "event-" + id, payload, NOW);
        ReflectionTestUtils.setField(row, "id", id);
        return row;
    }
}
