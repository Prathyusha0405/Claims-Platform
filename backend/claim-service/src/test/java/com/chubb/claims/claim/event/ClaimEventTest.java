package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimEventTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00.123456789Z");

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void theIdIsTheSameForTheSameEventWhereverItIsComputed() {
        assertThat(approved().id()).isEqualTo(approved().id());
        assertThat(approved().id()).hasSize(36);
    }

    @Test
    void theIdChangesWithWhatHappenedToWhichClaimByWhomAndWhen() {
        ClaimEvent base = approved();

        assertThat(new ClaimEvent(ClaimEventType.CLAIM_REJECTED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.REJECTED, 7L, "CLAIMS_STAFF", "x", NOW).id()).isNotEqualTo(base.id());
        assertThat(new ClaimEvent(ClaimEventType.CLAIM_APPROVED, 11L, "CLM-2026-000002", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 7L, "CLAIMS_STAFF", "x", NOW).id()).isNotEqualTo(base.id());
        assertThat(new ClaimEvent(ClaimEventType.CLAIM_APPROVED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 8L, "CLAIMS_STAFF", "x", NOW).id()).isNotEqualTo(base.id());
        assertThat(new ClaimEvent(ClaimEventType.CLAIM_APPROVED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 7L, "CLAIMS_STAFF", "x", NOW.plusNanos(1)).id())
                .isNotEqualTo(base.id());
    }

    @Test
    void anEventSurvivesBeingWrittenAsJsonAndReadBack() throws Exception {
        ClaimEvent event = approved();

        ClaimEvent readBack = objectMapper.readValue(objectMapper.writeValueAsString(event), ClaimEvent.class);

        assertThat(readBack).isEqualTo(event);
        assertThat(readBack.id()).isEqualTo(event.id());
    }

    @Test
    void theJsonCarriesTheRecordComponentsAndNothingElse() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(approved()));

        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("type", "claimId", "claimNumber", "claimantId", "assignedStaffId",
                "fromStatus", "toStatus", "actorId", "actorRole", "details", "occurredAt");
        assertThat(json.get("type").asText()).isEqualTo("CLAIM_APPROVED");
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-10-22T06:00:00.123456789Z");
    }

    private static ClaimEvent approved() {
        return new ClaimEvent(ClaimEventType.CLAIM_APPROVED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, 7L, "CLAIMS_STAFF", "Claim approved", NOW);
    }
}
