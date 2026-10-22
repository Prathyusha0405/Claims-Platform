package com.chubb.claims.claim.service;

import com.chubb.claims.claim.dto.MarkedReadResponse;
import com.chubb.claims.claim.dto.NotificationResponse;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.dto.UnreadCountResponse;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.exception.NotificationNotFoundException;
import com.chubb.claims.claim.repository.NotificationRepository;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Scoping to the caller, sort validation and the mark-as-read rules; the SQL itself is covered by the repository test. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");
    private static final AuthenticatedUser JANE = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    // ---- inbox ------------------------------------------------------------------------------------------------

    @Test
    void theInboxIsTheCallersOwnAndDefaultsToNewestFirstWithAStableTieBreak() {
        when(notificationRepository.findInbox(eq(5L), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(item(1L, false))));

        PageResponse<NotificationResponse> page = notificationService.getNotifications(JANE, false, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(NotificationResponse::id).containsExactly(1L);
        assertThat(sortUsed(false)).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    }

    @Test
    void anExplicitDirectionIsKeptAndTheTieBreakFollowsIt() {
        when(notificationRepository.findInbox(eq(5L), eq(false), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        notificationService.getNotifications(JANE, false, PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "createdAt")));

        assertThat(sortUsed(false)).containsExactly(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    void pagingAndTheUnreadFilterArePassedThrough() {
        when(notificationRepository.findInbox(eq(5L), eq(true), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        notificationService.getNotifications(JANE, true, PageRequest.of(3, 7));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(notificationRepository).findInbox(eq(5L), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(3);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(7);
    }

    @Test
    void onlyCreatedAtIsSortable() {
        assertThatThrownBy(() -> notificationService.getNotifications(JANE, false, PageRequest.of(0, 20, Sort.by("message"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Unsupported sort field 'message'; allowed fields: [createdAt]");

        verifyNoInteractions(notificationRepository);
    }

    // ---- unread badge -----------------------------------------------------------------------------------------

    @Test
    void theUnreadCountIsTheCallers() {
        when(notificationRepository.countByRecipientIdAndReadFalse(5L)).thenReturn(4L);

        assertThat(notificationService.countUnread(JANE)).isEqualTo(new UnreadCountResponse(4));
    }

    // ---- mark as read -----------------------------------------------------------------------------------------

    @Test
    void markingReadUpdatesThenReturnsTheNotificationAsItNowStands() {
        when(notificationRepository.findInboxItem(10L, 5L)).thenReturn(Optional.of(item(10L, true)));

        NotificationResponse response = notificationService.markRead(10L, JANE);

        assertThat(response.read()).isTrue();
        InOrder order = inOrder(notificationRepository);
        order.verify(notificationRepository).markRead(10L, 5L);
        order.verify(notificationRepository).findInboxItem(10L, 5L);
    }

    @Test
    void markingSomeoneElsesOrAMissingNotificationIs404() {
        when(notificationRepository.findInboxItem(10L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(10L, JANE))
                .isInstanceOf(NotificationNotFoundException.class)
                .hasMessage("Notification '10' was not found");
    }

    @Test
    void markAllReadReportsHowManyWereUnread() {
        when(notificationRepository.markAllRead(5L)).thenReturn(3);

        assertThat(notificationService.markAllRead(JANE)).isEqualTo(new MarkedReadResponse(3));
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private Sort sortUsed(boolean unreadOnly) {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(notificationRepository).findInbox(eq(5L), eq(unreadOnly), pageable.capture());
        return pageable.getValue().getSort();
    }

    private static NotificationResponse item(Long id, boolean read) {
        return new NotificationResponse(id, 100L, "CLM-2026-000100", ClaimEventType.CLAIM_CREATED,
                "Your claim CLM-2026-000100 has been submitted successfully.", read, NOW);
    }
}
