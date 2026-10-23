package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.ClaimTrendResponse;
import com.chubb.claims.claim.dto.ClaimTrendResponse.Granularity;
import com.chubb.claims.claim.dto.TrendCriteria;
import com.chubb.claims.claim.dto.TrendRow;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Market;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.repository.ClaimRepository;
import com.chubb.claims.claim.repository.ClaimScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimTrendServiceTest {

    @Mock
    private ClaimRepository claimRepository;

    @InjectMocks
    private ClaimTrendService service;

    @Test
    void theClaimsAreChosenByTheRepositoryAndPutIntoPeriodsHere() {
        TrendCriteria criteria = new TrendCriteria(Market.SG, ClaimType.AUTO, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        when(claimRepository.findTrendRows(ClaimScope.of(criteria))).thenReturn(List.of(
                new TrendRow(Instant.parse("2026-08-05T10:00:00Z"), ClaimStatus.SUBMITTED),
                new TrendRow(Instant.parse("2026-08-06T10:00:00Z"), ClaimStatus.APPROVED)));

        ClaimTrendResponse trend = service.trend(criteria);

        assertThat(trend.granularity()).isEqualTo(Granularity.WEEK);
        assertThat(trend.periods().get(1).start()).isEqualTo(LocalDate.of(2026, 8, 3));
        assertThat(trend.periods().get(1).claimsByStatus()).containsEntry(ClaimStatus.SUBMITTED, 1L).containsEntry(ClaimStatus.APPROVED, 1L);
        // 1 Aug 2026 is a Saturday, so a window from the 1st starts in the week of Monday 27 July and ends in the week of 31 Aug.
        assertThat(trend.periods().get(0).start()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(trend.periods().get(trend.periods().size() - 1).start()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    @Test
    void aWindowThatEndsBeforeItStartsIsRefusedBeforeTheDatabaseIsAsked() {
        TrendCriteria backwards = new TrendCriteria(null, null, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 1));

        assertThatThrownBy(() -> service.trend(backwards)).isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(claimRepository);
    }
}
