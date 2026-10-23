package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.StaffStatusTotal;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.entity.Claim;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The aggregates behind the dashboards, against a real H2 database created by the Flyway migrations. They are grouped by
 * market and currency as well as status, because amounts in different currencies must never be summed together.
 */
@DataJpaTest
class ClaimStatisticsRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Autowired
    private ClaimRepository claimRepository;

    @BeforeEach
    void setUp() {
        claimRepository.save(claim("CLM-1", "100.00", Market.SG));                               // SUBMITTED, unassigned, SGD
        claimRepository.save(claim("CLM-2", "25050", Market.JP));                                // SUBMITTED, unassigned, JPY
        claimRepository.save(reviewed("CLM-3", "1000.00", 7L, ClaimStatus.UNDER_REVIEW, Market.SG)); // staff member 7, SGD
        claimRepository.save(reviewed("CLM-4", "2000.00", 7L, ClaimStatus.UNDER_REVIEW, Market.AU)); // staff member 7, AUD
        claimRepository.save(reviewed("CLM-5", "500.00", 7L, ClaimStatus.APPROVED, Market.SG));      // staff member 7, SGD
        claimRepository.saveAndFlush(reviewed("CLM-6", "40.00", 8L, ClaimStatus.REJECTED, Market.SG)); // staff member 8, SGD
    }

    @Test
    void totalsAreGroupedByMarketAndStatusAndCarryTheMarketsCurrency() {
        List<StatusTotal> totals = claimRepository.totalsByStatus();

        assertThat(totals).hasSize(6);
        assertTotal(totals, Market.SG, ClaimStatus.SUBMITTED, 1, "100.00");
        assertTotal(totals, Market.JP, ClaimStatus.SUBMITTED, 1, "25050.00");
        assertTotal(totals, Market.SG, ClaimStatus.UNDER_REVIEW, 1, "1000.00");
        assertTotal(totals, Market.AU, ClaimStatus.UNDER_REVIEW, 1, "2000.00");
        assertTotal(totals, Market.SG, ClaimStatus.APPROVED, 1, "500.00");
        assertTotal(totals, Market.SG, ClaimStatus.REJECTED, 1, "40.00");
        assertThat(totals).allSatisfy(total -> assertThat(total.currency()).isEqualTo(total.market().currency()));
        assertThat(totals).extracting(StatusTotal::status).doesNotContain(ClaimStatus.SETTLED, ClaimStatus.MORE_INFO_REQUIRED);
    }

    @Test
    void totalsByStaffMemberGroupAssignedClaimsOnlyAndKeepTheirMarket() {
        List<StaffStatusTotal> totals = claimRepository.totalsByStaffAndStatus();

        // (7, SG, UNDER_REVIEW) (7, AU, UNDER_REVIEW) (7, SG, APPROVED) (8, SG, REJECTED); the unassigned claims do not appear.
        assertThat(totals).hasSize(4);
        assertStaffMemberTotal(totals, 7L, Market.SG, ClaimStatus.UNDER_REVIEW, 1, "1000.00");
        assertStaffMemberTotal(totals, 7L, Market.AU, ClaimStatus.UNDER_REVIEW, 1, "2000.00");
        assertStaffMemberTotal(totals, 7L, Market.SG, ClaimStatus.APPROVED, 1, "500.00");
        assertStaffMemberTotal(totals, 8L, Market.SG, ClaimStatus.REJECTED, 1, "40.00");
        assertThat(totals).allSatisfy(total -> assertThat(total.currency()).isEqualTo(total.market().currency()));
        assertThat(totals).extracting(StaffStatusTotal::staffId).doesNotContainNull();
        assertThat(totals).extracting(StaffStatusTotal::status).doesNotContain(ClaimStatus.SUBMITTED);
    }

    @Test
    void anEmptyTableYieldsNoRows() {
        claimRepository.deleteAll();

        assertThat(claimRepository.totalsByStatus()).isEmpty();
        assertThat(claimRepository.totalsByStaffAndStatus()).isEmpty();
    }

    private static void assertTotal(List<StatusTotal> totals, Market market, ClaimStatus status, long count, String amount) {
        assertThat(totals).filteredOn(total -> total.market() == market && total.status() == status)
                .singleElement().satisfies(total -> {
                    assertThat(total.claimCount()).isEqualTo(count);
                    assertThat(total.totalAmount()).isEqualByComparingTo(amount);
                });
    }

    private static void assertStaffMemberTotal(List<StaffStatusTotal> totals, Long staffId, Market market, ClaimStatus status,
                                           long count, String amount) {
        assertThat(totals).filteredOn(total -> total.staffId().equals(staffId) && total.market() == market
                        && total.status() == status)
                .singleElement().satisfies(total -> {
                    assertThat(total.claimCount()).isEqualTo(count);
                    assertThat(total.totalAmount()).isEqualByComparingTo(amount);
                });
    }

    private static Claim claim(String number, String amount, Market market) {
        return new Claim(number, "POL-STATS", 1L, "Claimant", "c@example.com", ClaimType.AUTO, market,
                LocalDate.of(2026, 10, 1), null, "stats test", new BigDecimal(amount), NOW);
    }

    /** Builds a claim already in {@code target}, assigned to the staff member (UNDER_REVIEW first, then onwards). */
    private static Claim reviewed(String number, String amount, Long staffId, ClaimStatus target, Market market) {
        Claim claim = claim(number, amount, market);
        claim.assignTo(staffId, NOW);
        if (target != ClaimStatus.UNDER_REVIEW) {
            claim.transitionTo(target, NOW);
        }
        return claim;
    }
}
