package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimHistory;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.Notification;
import com.chubb.claims.claim.repository.ClaimHistoryRepository;
import com.chubb.claims.claim.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalClaimEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @Mock
    private ClaimHistoryRepository claimHistoryRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ClaimsStaffDirectory claimsStaffDirectory;

    @InjectMocks
    private LocalClaimEventPublisher publisher;

    @Captor
    private ArgumentCaptor<ClaimHistory> historyCaptor;

    @Captor
    private ArgumentCaptor<List<Notification>> notificationsCaptor;

    @Test
    void publishAuditsTheEventAndNotifiesTheClaimant() {
        publisher.publish(new ClaimEvent(ClaimEventType.CLAIM_CREATED, 10L, "CLM-2026-000001", 5L, null,
                null, ClaimStatus.SUBMITTED, 5L, "CLAIMANT", "Claim submitted", NOW));

        verify(claimHistoryRepository).save(historyCaptor.capture());
        ClaimHistory history = historyCaptor.getValue();
        assertThat(history.getClaimId()).isEqualTo(10L);
        assertThat(history.getEventType()).isEqualTo(ClaimEventType.CLAIM_CREATED);
        assertThat(history.getFromStatus()).isNull();
        assertThat(history.getToStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(history.getPerformedBy()).isEqualTo(5L);
        assertThat(history.getPerformedByRole()).isEqualTo("CLAIMANT");
        assertThat(history.getDetails()).isEqualTo("Claim submitted");
        assertThat(history.getCreatedAt()).isEqualTo(NOW);

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        assertThat(notificationsCaptor.getValue()).singleElement().satisfies(notification -> {
            assertThat(notification.getRecipientId()).isEqualTo(5L);
            assertThat(notification.getClaimId()).isEqualTo(10L);
            assertThat(notification.getMessage()).contains("CLM-2026-000001");
        });
    }

    @Test
    void aNewClaimAlsoNotifiesEveryClaimsStaffMemberTheDirectoryKnows() {
        when(claimsStaffDirectory.claimsStaffIds()).thenReturn(List.of(7L, 9L));

        publisher.publish(new ClaimEvent(ClaimEventType.CLAIM_CREATED, 10L, "CLM-2026-000001", 5L, null,
                null, ClaimStatus.SUBMITTED, 5L, "CLAIMANT", "Claim submitted", NOW));

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        assertThat(notificationsCaptor.getValue()).extracting(Notification::getRecipientId).containsExactly(5L, 7L, 9L);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimEventType.class, mode = EnumSource.Mode.EXCLUDE, names = "CLAIM_CREATED")
    void onlyANewClaimAsksWhoTheClaimsStaffAre(ClaimEventType type) {
        publisher.publish(new ClaimEvent(type, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.UNDER_REVIEW, 7L, "CLAIMS_STAFF", "details", NOW));

        verifyNoInteractions(claimsStaffDirectory);
    }

    @Test
    void publishRecordsStatusChangesAndNotifiesTheStaffMemberToo() {
        publisher.publish(new ClaimEvent(ClaimEventType.ADDITIONAL_INFO_RECEIVED, 10L, "CLM-2026-000001", 5L, 7L,
                ClaimStatus.MORE_INFO_REQUIRED, ClaimStatus.UNDER_REVIEW, 5L, "CLAIMANT", "Estimate attached", NOW));

        verify(claimHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getFromStatus()).isEqualTo(ClaimStatus.MORE_INFO_REQUIRED);
        assertThat(historyCaptor.getValue().getToStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(historyCaptor.getValue().getDetails()).isEqualTo("Estimate attached");

        verify(notificationRepository).saveAll(notificationsCaptor.capture());
        assertThat(notificationsCaptor.getValue()).extracting(Notification::getRecipientId).containsExactly(5L, 7L);
    }
}
