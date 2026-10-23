package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.ClaimSearchCriteria;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Currency;
import com.chubb.claims.claim.entity.Market;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the dynamic search query against a real H2 database created by the Flyway migrations, so a wrong
 * attribute name or LIKE/date handling fails here rather than in production.
 */
@DataJpaTest
class ClaimSearchRepositoryTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 50, Sort.by("claimNumber"));

    @Autowired
    private ClaimRepository claimRepository;

    @BeforeEach
    void setUp() {
        // A: claimant 1, SUBMITTED, early October
        claimRepository.save(claim("CLM-2026-000001", "POL-AAA-1", 1L, "2026-10-01T10:00:00Z", "100.00"));

        // B: claimant 1, UNDER_REVIEW, last second of 10 October
        Claim b = claim("CLM-2026-000002", "POL-BBB-2", 1L, "2026-10-10T23:59:59Z", "200.00");
        b.transitionTo(ClaimStatus.UNDER_REVIEW, Instant.parse("2026-10-11T08:00:00Z"));
        claimRepository.save(b);

        // C: claimant 2, SUBMITTED, assigned to staff member 7, first second of 15 October
        Claim c = claim("CLM-2026-000003", "POL-AAA-3", 2L, "2026-10-15T00:00:00Z", "300.00");
        ReflectionTestUtils.setField(c, "assignedStaffId", 7L);
        ReflectionTestUtils.setField(c, "market", Market.NZ);   // A and B are Singapore (the helper's default)
        ReflectionTestUtils.setField(c, "currency", Currency.NZD); // the database insists a claim is in its market's currency
        claimRepository.saveAndFlush(c);
    }

    @Test
    void noFiltersReturnsEverything() {
        assertThat(search(criteria(null, null, null, null, null, null), null))
                .containsExactly("CLM-2026-000001", "CLM-2026-000002", "CLM-2026-000003");
    }

    @Test
    void claimNumberIsAPartialCaseInsensitiveMatch() {
        assertThat(search(criteria("clm-2026-0000", null, null, null, null, null), null)).hasSize(3);
        assertThat(search(criteria("000002", null, null, null, null, null), null)).containsExactly("CLM-2026-000002");
        assertThat(search(criteria("  Clm-2026-000003 ", null, null, null, null, null), null)).containsExactly("CLM-2026-000003");
    }

    @Test
    void policyNumberIsAPartialCaseInsensitiveMatch() {
        assertThat(search(criteria(null, "pol-aaa", null, null, null, null), null))
                .containsExactly("CLM-2026-000001", "CLM-2026-000003");
    }

    @Test
    void statusIsAnExactMatch() {
        assertThat(search(criteria(null, null, ClaimStatus.UNDER_REVIEW, null, null, null), null))
                .containsExactly("CLM-2026-000002");
        assertThat(search(criteria(null, null, ClaimStatus.APPROVED, null, null, null), null)).isEmpty();
    }

    @Test
    void marketIsAnExactMatch() {
        assertThat(search(inMarket(Market.SG, null), null)).containsExactly("CLM-2026-000001", "CLM-2026-000002");
        assertThat(search(inMarket(Market.NZ, null), null)).containsExactly("CLM-2026-000003");
        assertThat(search(inMarket(Market.JP, null), null)).isEmpty();
    }

    @Test
    void marketIsCombinedWithTheOtherFiltersAndWithTheClaimantRestriction() {
        assertThat(search(inMarket(Market.SG, ClaimStatus.UNDER_REVIEW), null)).containsExactly("CLM-2026-000002");
        assertThat(search(inMarket(Market.NZ, ClaimStatus.UNDER_REVIEW), null)).isEmpty();
        assertThat(search(inMarket(Market.SG, null), 1L)).containsExactly("CLM-2026-000001", "CLM-2026-000002");
        assertThat(search(inMarket(Market.SG, null), 2L)).isEmpty();
    }

    @Test
    void claimsCanBeSortedByMarket() {
        Page<Claim> page = claimRepository.findAll(ClaimSpecifications.matching(criteria(null, null, null, null, null, null), null),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "market", "claimNumber")));

        assertThat(page.getContent()).extracting(Claim::getMarket).containsExactly(Market.NZ, Market.SG, Market.SG);
    }

    @Test
    void assignedStaffMemberFiltersByStaffMemberId() {
        assertThat(search(criteria(null, null, null, null, null, 7L), null)).containsExactly("CLM-2026-000003");
        assertThat(search(criteria(null, null, null, null, null, 99L), null)).isEmpty();
    }

    @Test
    void dateRangeIncludesBothEndDaysInUtc() {
        // B was created at 23:59:59 on the 10th: still "10 October".
        assertThat(search(criteria(null, null, null, date("2026-10-10"), date("2026-10-10"), null), null))
                .containsExactly("CLM-2026-000002");
        // C was created at 00:00:00 on the 15th: the first instant of that day counts.
        assertThat(search(criteria(null, null, null, date("2026-10-15"), null, null), null))
                .containsExactly("CLM-2026-000003");
        assertThat(search(criteria(null, null, null, null, date("2026-10-09"), null), null))
                .containsExactly("CLM-2026-000001");
        assertThat(search(criteria(null, null, null, date("2026-10-02"), date("2026-10-14"), null), null))
                .containsExactly("CLM-2026-000002");
    }

    @Test
    void restrictingToAClaimantHidesEveryoneElsesClaims() {
        assertThat(search(criteria(null, null, null, null, null, null), 1L))
                .containsExactly("CLM-2026-000001", "CLM-2026-000002");
        assertThat(search(criteria(null, null, null, null, null, null), 2L)).containsExactly("CLM-2026-000003");
        assertThat(search(criteria(null, null, null, null, null, null), 3L)).isEmpty();
    }

    @Test
    void filtersAreCombinedWithAnd() {
        assertThat(search(criteria("CLM", "AAA", ClaimStatus.SUBMITTED, date("2026-10-01"), date("2026-10-31"), null), 1L))
                .containsExactly("CLM-2026-000001");
        assertThat(search(criteria("CLM", "BBB", ClaimStatus.SUBMITTED, null, null, null), null)).isEmpty();
    }

    @Test
    void blankTextFiltersAreIgnored() {
        assertThat(search(criteria("   ", "", null, null, null, null), null)).hasSize(3);
    }

    @Test
    void likeWildcardsInUserInputAreTreatedAsLiterals() {
        assertThat(search(criteria("%", null, null, null, null, null), null)).isEmpty();
        assertThat(search(criteria("CLM_2026", null, null, null, null, null), null)).isEmpty();
        assertThat(search(criteria(null, "POL%", null, null, null, null), null)).isEmpty();
        assertThat(search(criteria("\\", null, null, null, null, null), null)).isEmpty();
    }

    @Test
    void pagingAndSortingWorkTogether() {
        ClaimSearchCriteria all = criteria(null, null, null, null, null, null);

        Page<Claim> first = claimRepository.findAll(ClaimSpecifications.matching(all, null),
                PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "claimNumber")));
        Page<Claim> second = claimRepository.findAll(ClaimSpecifications.matching(all, null),
                PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "claimNumber")));

        assertThat(first.getContent()).extracting(Claim::getClaimNumber).containsExactly("CLM-2026-000003", "CLM-2026-000002");
        assertThat(second.getContent()).extracting(Claim::getClaimNumber).containsExactly("CLM-2026-000001");
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
    }

    @Test
    void sortingByAmountWorks() {
        Page<Claim> page = claimRepository.findAll(
                ClaimSpecifications.matching(criteria(null, null, null, null, null, null), null),
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "claimAmount")));

        assertThat(page.getContent()).extracting(Claim::getClaimAmount)
                .containsExactly(new BigDecimal("300.00"), new BigDecimal("200.00"), new BigDecimal("100.00"));
    }

    @Test
    void findByClaimantIdPagesOnlyThatClaimantsClaims() {
        Page<Claim> page = claimRepository.findByClaimantId(1L, PageRequest.of(0, 1, Sort.by("claimNumber")));

        assertThat(page.getContent()).extracting(Claim::getClaimNumber).containsExactly("CLM-2026-000001");
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private List<String> search(ClaimSearchCriteria criteria, Long restrictToClaimantId) {
        return claimRepository.findAll(ClaimSpecifications.matching(criteria, restrictToClaimantId), FIRST_PAGE)
                .map(Claim::getClaimNumber).getContent();
    }

    private static ClaimSearchCriteria criteria(String claimNumber, String policyNumber, ClaimStatus status,
                                                LocalDate from, LocalDate to, Long staffId) {
        return new ClaimSearchCriteria(claimNumber, policyNumber, status, null, from, to, staffId);
    }

    private static ClaimSearchCriteria inMarket(Market market, ClaimStatus status) {
        return new ClaimSearchCriteria(null, null, status, market, null, null, null);
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    private static Claim claim(String claimNumber, String policyNumber, Long claimantId, String createdAt, String amount) {
        return new Claim(claimNumber, policyNumber, claimantId, "Claimant " + claimantId, "c" + claimantId + "@example.com",
                ClaimType.AUTO, Market.SG, LocalDate.of(2026, 9, 30), "Singapore", "Test claim", new BigDecimal(amount),
                Instant.parse(createdAt));
    }
}
