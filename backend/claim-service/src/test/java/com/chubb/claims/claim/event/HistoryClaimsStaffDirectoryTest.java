package com.chubb.claims.claim.event;

import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryClaimsStaffDirectoryTest {

    @Mock
    private ClaimHistoryRepository claimHistoryRepository;

    @InjectMocks
    private HistoryClaimsStaffDirectory directory;

    @Test
    void theClaimsStaffAreWhoeverHasActedAsClaimsStaff() {
        when(claimHistoryRepository.findActorIdsByRole("CLAIMS_STAFF")).thenReturn(List.of(2L, 3L));

        assertThat(directory.claimsStaffIds()).containsExactly(2L, 3L);
        verify(claimHistoryRepository).findActorIdsByRole("CLAIMS_STAFF");
    }

    @Test
    void nobodyIsKnownWhenNobodyHasActed() {
        when(claimHistoryRepository.findActorIdsByRole("CLAIMS_STAFF")).thenReturn(List.of());

        assertThat(directory.claimsStaffIds()).isEmpty();
    }
}
