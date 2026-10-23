package com.chubb.claims.claim.entity;

import com.chubb.claims.claim.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.chubb.claims.claim.entity.ClaimStatus.APPROVED;
import static com.chubb.claims.claim.entity.ClaimStatus.MORE_INFO_REQUIRED;
import static com.chubb.claims.claim.entity.ClaimStatus.REJECTED;
import static com.chubb.claims.claim.entity.ClaimStatus.SETTLED;
import static com.chubb.claims.claim.entity.ClaimStatus.SUBMITTED;
import static com.chubb.claims.claim.entity.ClaimStatus.UNDER_REVIEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimStatusTest {

    /** The lifecycle from CLAUDE.md, written out independently of the implementation. */
    private static final Map<ClaimStatus, Set<ClaimStatus>> EXPECTED = Map.of(
            SUBMITTED, EnumSet.of(UNDER_REVIEW),
            UNDER_REVIEW, EnumSet.of(MORE_INFO_REQUIRED, APPROVED, REJECTED),
            MORE_INFO_REQUIRED, EnumSet.of(UNDER_REVIEW),
            APPROVED, EnumSet.of(SETTLED),
            REJECTED, EnumSet.of(SETTLED),
            SETTLED, EnumSet.noneOf(ClaimStatus.class));

    @Test
    void everyStatusPairMatchesTheDocumentedLifecycle() {
        for (ClaimStatus from : ClaimStatus.values()) {
            for (ClaimStatus to : ClaimStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(EXPECTED.get(from).contains(to));
            }
        }
    }

    @Test
    void settledIsTerminal() {
        assertThat(SETTLED.allowedTransitions()).isEmpty();
    }

    @Test
    void invalidTransitionNamesBothStatusesAndTheAllowedOnes() {
        assertThatThrownBy(() -> SUBMITTED.requireTransitionTo(APPROVED))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("SUBMITTED")
                .hasMessageContaining("APPROVED")
                .hasMessageContaining("UNDER_REVIEW");
    }

    @Test
    void claimMovesAlongTheLifecycleAndRecordsWhen() {
        Instant created = Instant.parse("2026-10-22T06:00:00Z");
        Instant reviewed = created.plusSeconds(60);
        Claim claim = new Claim("CLM-2026-000001", "POL-1", 5L, "Jane", "jane@example.com", ClaimType.AUTO, Market.SG,
                LocalDate.of(2026, 10, 1), null, "desc", new BigDecimal("100.00"), created);

        claim.transitionTo(UNDER_REVIEW, reviewed);

        assertThat(claim.getStatus()).isEqualTo(UNDER_REVIEW);
        assertThat(claim.getUpdatedAt()).isEqualTo(reviewed);
        assertThat(claim.getCreatedAt()).isEqualTo(created);
    }

    @Test
    void claimRefusesAnIllegalTransitionAndStaysUnchanged() {
        Instant created = Instant.parse("2026-10-22T06:00:00Z");
        Claim claim = new Claim("CLM-2026-000001", "POL-1", 5L, "Jane", "jane@example.com", ClaimType.AUTO, Market.SG,
                LocalDate.of(2026, 10, 1), null, "desc", new BigDecimal("100.00"), created);

        assertThatThrownBy(() -> claim.transitionTo(SETTLED, created.plusSeconds(60)))
                .isInstanceOf(InvalidStatusTransitionException.class);

        assertThat(claim.getStatus()).isEqualTo(SUBMITTED);
        assertThat(claim.getUpdatedAt()).isEqualTo(created);
    }
}
