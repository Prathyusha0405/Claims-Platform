package com.chubb.claims.claim.event;

import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimStatus;
import com.chubb.claims.claim.entity.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaimNotificationFactoryTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    @ParameterizedTest
    @EnumSource(ClaimEventType.class)
    void everyEventTellsTheClaimantWhichClaimItIsAbout(ClaimEventType type) {
        List<Notification> notifications = notificationsFor(event(type, null));

        Notification forClaimant = notifications.get(0);
        assertThat(forClaimant.getRecipientId()).isEqualTo(5L);
        assertThat(forClaimant.getClaimId()).isEqualTo(10L);
        assertThat(forClaimant.getEventType()).isEqualTo(type);
        assertThat(forClaimant.getMessage()).contains("CLM-2026-000001").hasSizeLessThanOrEqualTo(500);
        assertThat(forClaimant.isRead()).isFalse();
        assertThat(forClaimant.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void messagesDescribeWhatHappened() {
        assertThat(messageFor(ClaimEventType.CLAIM_CREATED)).contains("submitted");
        assertThat(messageFor(ClaimEventType.CLAIM_ASSIGNED)).contains("assigned");
        assertThat(messageFor(ClaimEventType.CLAIM_APPROVED)).contains("approved");
        assertThat(messageFor(ClaimEventType.CLAIM_REJECTED)).contains("rejected");
        assertThat(messageFor(ClaimEventType.ADDITIONAL_INFO_REQUESTED)).contains("Additional information is required");
        assertThat(messageFor(ClaimEventType.ADDITIONAL_INFO_RECEIVED)).contains("received");
    }

    @Test
    void theAssignedStaffMemberIsToldWhenTheClaimantAnswersTheirRequest() {
        List<Notification> notifications = notificationsFor(
                event(ClaimEventType.ADDITIONAL_INFO_RECEIVED, 7L));

        assertThat(notifications).hasSize(2);
        assertThat(notifications).extracting(Notification::getRecipientId).containsExactly(5L, 7L);
        assertThat(notifications.get(1).getMessage()).contains("claimant has provided additional information");
    }

    @Test
    void noStaffMemberNotificationWhenNobodyIsAssigned() {
        assertThat(notificationsFor(event(ClaimEventType.ADDITIONAL_INFO_RECEIVED, null)))
                .hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimEventType.class, mode = EnumSource.Mode.EXCLUDE, names = "ADDITIONAL_INFO_RECEIVED")
    void otherEventsOnlyNotifyTheClaimantEvenWhenAStaffMemberIsAssigned(ClaimEventType type) {
        assertThat(notificationsFor(event(type, 7L))).hasSize(1);
    }

    @Test
    void aNewClaimIsAnnouncedToEveryClaimsStaffMember() {
        List<Notification> notifications = ClaimNotificationFactory.notificationsFor(
                event(ClaimEventType.CLAIM_CREATED, null), List.of(7L, 8L));

        assertThat(notifications).extracting(Notification::getRecipientId).containsExactly(5L, 7L, 8L);
        assertThat(notifications.subList(1, 3)).allSatisfy(forStaff -> {
            assertThat(forStaff.getClaimId()).isEqualTo(10L);
            assertThat(forStaff.getEventType()).isEqualTo(ClaimEventType.CLAIM_CREATED);
            assertThat(forStaff.getMessage())
                    .isEqualTo("A new claim CLM-2026-000001 has been submitted and is waiting in the work queue.");
            assertThat(forStaff.isRead()).isFalse();
            assertThat(forStaff.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aNewClaimWithNoKnownStaffOnlyTellsTheClaimant() {
        assertThat(notificationsFor(event(ClaimEventType.CLAIM_CREATED, null))).hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(value = ClaimEventType.class, mode = EnumSource.Mode.EXCLUDE, names = "CLAIM_CREATED")
    void theStaffListIsOnlyUsedForANewClaim(ClaimEventType type) {
        assertThat(ClaimNotificationFactory.notificationsFor(event(type, null), List.of(7L, 8L)))
                .extracting(Notification::getRecipientId).containsExactly(5L);
    }

    private static List<Notification> notificationsFor(ClaimEvent event) {
        return ClaimNotificationFactory.notificationsFor(event, List.of());
    }

    private static String messageFor(ClaimEventType type) {
        return notificationsFor(event(type, null)).get(0).getMessage();
    }

    private static ClaimEvent event(ClaimEventType type, Long assignedStaffId) {
        return new ClaimEvent(type, 10L, "CLM-2026-000001", 5L, assignedStaffId,
                ClaimStatus.UNDER_REVIEW, ClaimStatus.UNDER_REVIEW, 9L, "CLAIMS_STAFF", "details", NOW);
    }
}
