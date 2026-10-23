package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.StaffStatusTotal;
import com.chubb.claims.claim.dto.StatusTotal;
import com.chubb.claims.claim.dto.TrendRow;
import com.chubb.claims.claim.dto.TypeStatusTotal;
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
 * market and currency as well as status, because amounts in different currencies must never be summed together, and they
 * can be narrowed to a submission window and a claim type.
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
        List<StatusTotal> totals = claimRepository.totalsByStatus(ClaimScope.all());

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
        List<StaffStatusTotal> totals = claimRepository.totalsByStaffAndStatus(ClaimScope.all());

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

        assertThat(claimRepository.totalsByStatus(ClaimScope.all())).isEmpty();
        assertThat(claimRepository.totalsByStaffAndStatus(ClaimScope.all())).isEmpty();
        assertThat(claimRepository.totalsByTypeAndStatus(ClaimScope.all())).isEmpty();
        assertThat(claimRepository.findTrendRows(ClaimScope.all())).isEmpty();
    }

    // ---- claim type --------------------------------------------------------------------------------------------

    @Test
    void totalsByTypeKeepMarketCurrencyAndStatusForEveryType() {
        claimRepository.saveAndFlush(typed("CLM-7", "750.00", Market.SG, ClaimType.HOME, NOW));
        claimRepository.saveAndFlush(typed("CLM-8", "60.00", Market.SG, ClaimType.HOME, NOW));

        List<TypeStatusTotal> totals = claimRepository.totalsByTypeAndStatus(ClaimScope.all());

        assertThat(totals).filteredOn(total -> total.claimType() == ClaimType.HOME).singleElement().satisfies(total -> {
            assertThat(total.market()).isEqualTo(Market.SG);
            assertThat(total.currency()).isEqualTo(Market.SG.currency());
            assertThat(total.status()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(total.claimCount()).isEqualTo(2);
            assertThat(total.totalAmount()).isEqualByComparingTo("810.00");
        });
        assertThat(totals).filteredOn(total -> total.claimType() == ClaimType.AUTO)
                .extracting(TypeStatusTotal::claimCount).containsOnly(1L);
        assertThat(totals.stream().mapToLong(TypeStatusTotal::claimCount).sum()).isEqualTo(8);
    }

    @Test
    void aClaimTypeNarrowsTheStatusAndStaffTotalsButNeverTheTypeBreakdown() {
        claimRepository.saveAndFlush(reviewedOfType("CLM-7", "300.00", 9L, ClaimStatus.UNDER_REVIEW, Market.SG, ClaimType.HOME));
        ClaimScope homeOnly = new ClaimScope(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"), null, ClaimType.HOME);

        assertThat(claimRepository.totalsByStatus(homeOnly)).singleElement().satisfies(total -> {
            assertThat(total.market()).isEqualTo(Market.SG);
            assertThat(total.status()).isEqualTo(ClaimStatus.UNDER_REVIEW);
            assertThat(total.totalAmount()).isEqualByComparingTo("300.00");
        });
        assertThat(claimRepository.totalsByStaffAndStatus(homeOnly)).singleElement()
                .satisfies(total -> assertThat(total.staffId()).isEqualTo(9L));
        // The breakdown by type is how one type is read against the others, so it keeps every type.
        assertThat(claimRepository.totalsByTypeAndStatus(homeOnly)).extracting(TypeStatusTotal::claimType)
                .contains(ClaimType.AUTO, ClaimType.HOME);
    }

    @Test
    void aTypeWithNoClaimsGivesNoRows() {
        ClaimScope travelOnly = new ClaimScope(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"), null, ClaimType.TRAVEL);

        assertThat(claimRepository.totalsByStatus(travelOnly)).isEmpty();
        assertThat(claimRepository.totalsByStaffAndStatus(travelOnly)).isEmpty();
        assertThat(claimRepository.findTrendRows(travelOnly)).isEmpty();
    }

    // ---- submission window -------------------------------------------------------------------------------------

    @Test
    void aDateWindowKeepsWholeDaysAndIncludesBothEnds() {
        claimRepository.deleteAll();
        claimRepository.save(typed("CLM-A", "10.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-10-01T23:59:59Z"))); // the day before
        claimRepository.save(typed("CLM-B", "20.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-10-02T00:00:00Z"))); // first instant of "from"
        claimRepository.save(typed("CLM-C", "30.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-10-03T23:59:59Z"))); // last second of "to"
        claimRepository.saveAndFlush(typed("CLM-D", "40.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-10-04T00:00:00Z"))); // the day after

        ClaimScope window = ClaimScope.of(null, null, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3));

        assertThat(claimRepository.totalsByStatus(window)).singleElement().satisfies(total -> {
            assertThat(total.claimCount()).isEqualTo(2);
            assertThat(total.totalAmount()).isEqualByComparingTo("50.00");
        });
        assertThat(claimRepository.findTrendRows(window)).extracting(TrendRow::createdAt)
                .containsExactlyInAnyOrder(Instant.parse("2026-10-02T00:00:00Z"), Instant.parse("2026-10-03T23:59:59Z"));
    }

    @Test
    void eitherEndOfTheWindowCanBeLeftOpen() {
        claimRepository.deleteAll();
        claimRepository.save(typed("CLM-A", "10.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-09-30T12:00:00Z")));
        claimRepository.saveAndFlush(typed("CLM-B", "20.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-10-15T12:00:00Z")));

        assertThat(count(ClaimScope.of(null, null, LocalDate.of(2026, 10, 1), null))).isEqualTo(1);
        assertThat(count(ClaimScope.of(null, null, null, LocalDate.of(2026, 9, 30)))).isEqualTo(1);
        assertThat(count(ClaimScope.of(null, null, null, null))).isEqualTo(2);
    }

    @Test
    void theWindowAlsoNarrowsTheBreakdownByType() {
        claimRepository.deleteAll();
        claimRepository.save(typed("CLM-A", "10.00", Market.SG, ClaimType.AUTO, Instant.parse("2026-09-30T12:00:00Z")));
        claimRepository.saveAndFlush(typed("CLM-B", "20.00", Market.SG, ClaimType.HOME, Instant.parse("2026-10-15T12:00:00Z")));

        assertThat(claimRepository.totalsByTypeAndStatus(ClaimScope.of(null, null, LocalDate.of(2026, 10, 1), null)))
                .extracting(TypeStatusTotal::claimType).containsExactly(ClaimType.HOME);
    }

    // ---- trend rows --------------------------------------------------------------------------------------------

    @Test
    void trendRowsCarryWhenEachClaimWasSubmittedAndWhereItStands() {
        assertThat(claimRepository.findTrendRows(ClaimScope.all())).hasSize(6)
                .allSatisfy(row -> assertThat(row.createdAt()).isEqualTo(NOW));
        assertThat(claimRepository.findTrendRows(ClaimScope.all())).extracting(TrendRow::status)
                .containsExactlyInAnyOrder(ClaimStatus.SUBMITTED, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW,
                        ClaimStatus.UNDER_REVIEW, ClaimStatus.APPROVED, ClaimStatus.REJECTED);
    }

    @Test
    void trendRowsFollowTheMarketAndTheType() {
        claimRepository.saveAndFlush(typed("CLM-7", "75.00", Market.SG, ClaimType.HOME, NOW));

        assertThat(claimRepository.findTrendRows(new ClaimScope(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"), Market.JP, null)))
                .hasSize(1);
        assertThat(claimRepository.findTrendRows(new ClaimScope(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"), Market.SG, ClaimType.HOME)))
                .hasSize(1);
        assertThat(claimRepository.findTrendRows(new ClaimScope(Instant.EPOCH, Instant.parse("9999-12-31T00:00:00Z"), Market.SG, null)))
                .hasSize(5);
    }

    private long count(ClaimScope scope) {
        return claimRepository.totalsByStatus(scope).stream().mapToLong(StatusTotal::claimCount).sum();
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
        return typed(number, amount, market, ClaimType.AUTO, NOW);
    }

    private static Claim typed(String number, String amount, Market market, ClaimType type, Instant createdAt) {
        return new Claim(number, "POL-STATS", 1L, "Claimant", "c@example.com", type, market,
                LocalDate.of(2026, 10, 1), null, "stats test", new BigDecimal(amount), createdAt);
    }

    /** Builds a claim already in {@code target}, assigned to the staff member (UNDER_REVIEW first, then onwards). */
    private static Claim reviewed(String number, String amount, Long staffId, ClaimStatus target, Market market) {
        return reviewedOfType(number, amount, staffId, target, market, ClaimType.AUTO);
    }

    private static Claim reviewedOfType(String number, String amount, Long staffId, ClaimStatus target, Market market,
                                        ClaimType type) {
        Claim claim = typed(number, amount, market, type, NOW);
        claim.assignTo(staffId, NOW);
        if (target != ClaimStatus.UNDER_REVIEW) {
            claim.transitionTo(target, NOW);
        }
        return claim;
    }
}
