package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.OfficerStatusTotal;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The GROUP BY queries behind the dashboards, run against real H2 built by the Flyway migrations. */
@DataJpaTest
class ClaimStatisticsRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Autowired
    private ClaimRepository claimRepository;

    @BeforeEach
    void setUp() {
        claimRepository.save(claim("CLM-1", "100.00"));                                  // SUBMITTED, unassigned
        claimRepository.save(claim("CLM-2", "250.50"));                                  // SUBMITTED, unassigned
        claimRepository.save(reviewed("CLM-3", "1000.00", 7L, ClaimStatus.UNDER_REVIEW)); // officer 7
        claimRepository.save(reviewed("CLM-4", "2000.00", 7L, ClaimStatus.UNDER_REVIEW)); // officer 7
        claimRepository.save(reviewed("CLM-5", "500.00", 7L, ClaimStatus.APPROVED));      // officer 7
        claimRepository.saveAndFlush(reviewed("CLM-6", "40.00", 8L, ClaimStatus.REJECTED)); // officer 8
    }

    @Test
    void totalsByStatusCountAndSumEveryStatusThatHasClaims() {
        List<StatusTotal> totals = claimRepository.totalsByStatus();

        assertThat(totals).hasSize(4);
        assertTotal(totals, ClaimStatus.SUBMITTED, 2, "350.50");
        assertTotal(totals, ClaimStatus.UNDER_REVIEW, 2, "3000.00");
        assertTotal(totals, ClaimStatus.APPROVED, 1, "500.00");
        assertTotal(totals, ClaimStatus.REJECTED, 1, "40.00");
        assertThat(totals).extracting(StatusTotal::status).doesNotContain(ClaimStatus.SETTLED, ClaimStatus.MORE_INFO_REQUIRED);
    }

    @Test
    void totalsByOfficerGroupAssignedClaimsOnly() {
        List<OfficerStatusTotal> totals = claimRepository.totalsByOfficerAndStatus();

        // (7, UNDER_REVIEW) (7, APPROVED) (8, REJECTED); the two unassigned SUBMITTED claims do not appear.
        assertThat(totals).hasSize(3);
        assertOfficerTotal(totals, 7L, ClaimStatus.UNDER_REVIEW, 2, "3000.00");
        assertOfficerTotal(totals, 7L, ClaimStatus.APPROVED, 1, "500.00");
        assertOfficerTotal(totals, 8L, ClaimStatus.REJECTED, 1, "40.00");
        assertThat(totals).extracting(OfficerStatusTotal::officerId).doesNotContainNull();
        assertThat(totals).extracting(OfficerStatusTotal::status).doesNotContain(ClaimStatus.SUBMITTED);
    }

    @Test
    void anEmptyTableYieldsNoRows() {
        claimRepository.deleteAll();

        assertThat(claimRepository.totalsByStatus()).isEmpty();
        assertThat(claimRepository.totalsByOfficerAndStatus()).isEmpty();
    }

    private static void assertTotal(List<StatusTotal> totals, ClaimStatus status, long count, String amount) {
        assertThat(totals).filteredOn(total -> total.status() == status).singleElement().satisfies(total -> {
            assertThat(total.claimCount()).isEqualTo(count);
            assertThat(total.totalAmount()).isEqualByComparingTo(amount);
        });
    }

    private static void assertOfficerTotal(List<OfficerStatusTotal> totals, Long officerId, ClaimStatus status, long count,
                                           String amount) {
        assertThat(totals).filteredOn(total -> total.officerId().equals(officerId) && total.status() == status)
                .singleElement().satisfies(total -> {
                    assertThat(total.claimCount()).isEqualTo(count);
                    assertThat(total.totalAmount()).isEqualByComparingTo(amount);
                });
    }

    private static Claim claim(String number, String amount) {
        return new Claim(number, "POL-STATS", 1L, "Claimant", "c@example.com", ClaimType.AUTO,
                LocalDate.of(2026, 10, 1), null, "stats test", new BigDecimal(amount), NOW);
    }

    /** Builds a claim already in {@code target}, assigned to the officer (UNDER_REVIEW first, then onwards). */
    private static Claim reviewed(String number, String amount, Long officerId, ClaimStatus target) {
        Claim claim = claim(number, amount);
        claim.assignTo(officerId, NOW);
        if (target != ClaimStatus.UNDER_REVIEW) {
            claim.transitionTo(target, NOW);
        }
        return claim;
    }
}
