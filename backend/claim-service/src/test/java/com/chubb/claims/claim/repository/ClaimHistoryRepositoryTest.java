package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Who has acted under a role, read from the audit trail; run against real H2 built by the Flyway migrations. */
@DataJpaTest
class ClaimHistoryRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimHistoryRepository historyRepository;

    private Long claimId;

    @BeforeEach
    void setUp() {
        claimId = claimRepository.save(new Claim("CLM-2026-000001", "POL-HISTORY", 5L, "Jane Tan", "jane@example.com",
                ClaimType.AUTO, Market.SG, LocalDate.of(2026, 10, 1), null, "history test", new BigDecimal("100.00"), T0)).getId();
    }

    @Test
    void eachClaimsStaffMemberWhoHasActedIsListedOnceInIdOrder() {
        record(ClaimEventType.CLAIM_ASSIGNED, 9L, "CLAIMS_STAFF");
        record(ClaimEventType.ADDITIONAL_INFO_REQUESTED, 9L, "CLAIMS_STAFF");
        record(ClaimEventType.CLAIM_ASSIGNED, 7L, "CLAIMS_STAFF");
        record(ClaimEventType.CLAIM_APPROVED, 8L, "CLAIMS_STAFF");

        assertThat(historyRepository.findActorIdsByRole("CLAIMS_STAFF")).containsExactly(7L, 8L, 9L);
    }

    @Test
    void otherRolesAndUnknownActorsAreLeftOut() {
        record(ClaimEventType.CLAIM_CREATED, 5L, "CLAIMANT");
        record(ClaimEventType.CLAIM_ASSIGNED, 7L, "CLAIMS_STAFF");
        record(ClaimEventType.CLAIM_APPROVED, 3L, "MANAGER");
        record(ClaimEventType.CLAIM_REJECTED, null, "CLAIMS_STAFF");

        assertThat(historyRepository.findActorIdsByRole("CLAIMS_STAFF")).containsExactly(7L);
        assertThat(historyRepository.findActorIdsByRole("CLAIMANT")).containsExactly(5L);
        assertThat(historyRepository.findActorIdsByRole("NOBODY")).isEmpty();
    }

    @Test
    void nobodyIsKnownBeforeAnyoneHasActed() {
        assertThat(historyRepository.findActorIdsByRole("CLAIMS_STAFF")).isEmpty();
    }

    private void record(ClaimEventType type, Long actor, String role) {
        historyRepository.save(new ClaimHistory(claimId, type, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW, actor, role,
                "details", T0));
    }
}
