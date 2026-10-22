package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.NotificationResponse;
import com.chubb.claims.claim.entity.Claim;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.entity.ClaimType;
import com.chubb.claims.claim.entity.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The inbox queries and the mark-as-read updates, run against real H2 built by the Flyway migrations. */
@DataJpaTest
class NotificationRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-10-20T08:00:00Z");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));

    private static final long JANE = 5L;
    private static final long OFFICER = 7L;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private Claim claimA;
    private Claim claimB;
    private Notification janeRead;
    private Notification janeAssigned;
    private Notification janeSubmittedB;
    private Notification janeAssignedB;
    private Notification officerInfo;

    @BeforeEach
    void setUp() {
        claimA = claimRepository.save(claim("CLM-2026-000001"));
        claimB = claimRepository.save(claim("CLM-2026-000002"));

        janeRead = save(JANE, claimA, ClaimEventType.CLAIM_CREATED, T0, true);
        janeAssigned = save(JANE, claimA, ClaimEventType.CLAIM_ASSIGNED, T0.plusSeconds(3600), false);
        // Two raised in the same instant: the id decides their order.
        janeSubmittedB = save(JANE, claimB, ClaimEventType.CLAIM_CREATED, T0.plusSeconds(7200), false);
        janeAssignedB = save(JANE, claimB, ClaimEventType.CLAIM_ASSIGNED, T0.plusSeconds(7200), false);
        officerInfo = save(OFFICER, claimA, ClaimEventType.ADDITIONAL_INFO_RECEIVED, T0.plusSeconds(10800), false);
        notificationRepository.flush();
    }

    // ---- inbox ------------------------------------------------------------------------------------------------

    @Test
    void theInboxHoldsOnlyTheRecipientsNotificationsWithTheirClaimNumbers() {
        Page<NotificationResponse> inbox = notificationRepository.findInbox(JANE, false, PageRequest.of(0, 10, NEWEST_FIRST));

        assertThat(inbox.getTotalElements()).isEqualTo(4);
        assertThat(inbox.getContent()).extracting(NotificationResponse::id)
                .containsExactly(janeAssignedB.getId(), janeSubmittedB.getId(), janeAssigned.getId(), janeRead.getId());
        assertThat(inbox.getContent().get(0)).isEqualTo(new NotificationResponse(
                janeAssignedB.getId(), claimB.getId(), "CLM-2026-000002", ClaimEventType.CLAIM_ASSIGNED,
                janeAssignedB.getMessage(), false, T0.plusSeconds(7200)));
        assertThat(inbox.getContent().get(3).claimNumber()).isEqualTo("CLM-2026-000001");
        assertThat(inbox.getContent().get(3).read()).isTrue();
    }

    @Test
    void theOfficersInboxIsSeparateFromTheClaimants() {
        Page<NotificationResponse> inbox = notificationRepository.findInbox(OFFICER, false, PageRequest.of(0, 10, NEWEST_FIRST));

        assertThat(inbox.getContent()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(officerInfo.getId());
            assertThat(item.eventType()).isEqualTo(ClaimEventType.ADDITIONAL_INFO_RECEIVED);
            assertThat(item.claimNumber()).isEqualTo("CLM-2026-000001");
        });
    }

    @Test
    void unreadOnlyLeavesOutWhatHasBeenRead() {
        Page<NotificationResponse> unread = notificationRepository.findInbox(JANE, true, PageRequest.of(0, 10, NEWEST_FIRST));

        assertThat(unread.getTotalElements()).isEqualTo(3);
        assertThat(unread.getContent()).extracting(NotificationResponse::id)
                .containsExactly(janeAssignedB.getId(), janeSubmittedB.getId(), janeAssigned.getId());
        assertThat(unread.getContent()).allMatch(item -> !item.read());
    }

    @Test
    void sortingAndPagingFollowThePageable() {
        Page<NotificationResponse> first = notificationRepository.findInbox(JANE, false, PageRequest.of(0, 3, OLDEST_FIRST));
        Page<NotificationResponse> second = notificationRepository.findInbox(JANE, false, PageRequest.of(1, 3, OLDEST_FIRST));

        assertThat(first.getTotalElements()).isEqualTo(4);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(NotificationResponse::id)
                .containsExactly(janeRead.getId(), janeAssigned.getId(), janeSubmittedB.getId());
        assertThat(second.getContent()).extracting(NotificationResponse::id).containsExactly(janeAssignedB.getId());
        assertThat(second.isLast()).isTrue();
    }

    @Test
    void anUnknownRecipientHasAnEmptyInbox() {
        assertThat(notificationRepository.findInbox(999L, false, PageRequest.of(0, 10, NEWEST_FIRST)).getContent()).isEmpty();
    }

    // ---- a single entry ---------------------------------------------------------------------------------------

    @Test
    void anEntryIsVisibleToItsRecipientOnly() {
        assertThat(notificationRepository.findInboxItem(janeAssigned.getId(), JANE)).get()
                .extracting(NotificationResponse::claimNumber).isEqualTo("CLM-2026-000001");

        assertThat(notificationRepository.findInboxItem(janeAssigned.getId(), OFFICER)).isEmpty();
        assertThat(notificationRepository.findInboxItem(987_654L, JANE)).isEmpty();
    }

    // ---- mark as read -----------------------------------------------------------------------------------------

    @Test
    void markReadFlipsOnlyTheRecipientsOwnUnreadRow() {
        assertThat(notificationRepository.markRead(janeAssigned.getId(), JANE)).isEqualTo(1);

        assertThat(notificationRepository.findInboxItem(janeAssigned.getId(), JANE)).get()
                .extracting(NotificationResponse::read).isEqualTo(true);
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(JANE)).isEqualTo(2);
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(OFFICER)).isEqualTo(1);
    }

    @Test
    void markReadChangesNothingWhenItIsAlreadyReadOrBelongsToSomeoneElse() {
        assertThat(notificationRepository.markRead(janeRead.getId(), JANE)).isZero();           // already read
        assertThat(notificationRepository.markRead(janeAssigned.getId(), OFFICER)).isZero();    // not theirs
        assertThat(notificationRepository.markRead(987_654L, JANE)).isZero();                   // does not exist

        assertThat(notificationRepository.countByRecipientIdAndReadFalse(JANE)).isEqualTo(3);
    }

    @Test
    void markAllReadClearsTheRecipientsUnreadAndNobodyElses() {
        assertThat(notificationRepository.markAllRead(JANE)).isEqualTo(3);

        assertThat(notificationRepository.countByRecipientIdAndReadFalse(JANE)).isZero();
        assertThat(notificationRepository.countByRecipientIdAndReadFalse(OFFICER)).isEqualTo(1);
        assertThat(notificationRepository.markAllRead(JANE)).isZero();                          // nothing left to do
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private Notification save(long recipientId, Claim claim, ClaimEventType type, Instant createdAt, boolean read) {
        Notification notification = new Notification(recipientId, claim.getId(), type, type + " for " + claim.getClaimNumber(), createdAt);
        ReflectionTestUtils.setField(notification, "read", read);
        return notificationRepository.save(notification);
    }

    private static Claim claim(String number) {
        return new Claim(number, "POL-NOTIFY", JANE, "Jane Tan", "jane@example.com", ClaimType.AUTO,
                LocalDate.of(2026, 10, 1), null, "notification test", new BigDecimal("100.00"), T0);
    }
}
