package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.config.SecurityConfig;
import com.chubb.claims.claim.dto.MarkedReadResponse;
import com.chubb.claims.claim.dto.NotificationResponse;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.dto.UnreadCountResponse;
import com.chubb.claims.claim.entity.ClaimEventType;
import com.chubb.claims.claim.exception.InvalidRequestException;
import com.chubb.claims.claim.exception.NotificationNotFoundException;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.security.JwtService;
import com.chubb.claims.claim.security.Role;
import com.chubb.claims.claim.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-22T06:00:00Z");

    private static final AuthenticatedUser CLAIMANT = new AuthenticatedUser(5L, "jane@example.com", "Jane Tan", Role.CLAIMANT);
    private static final AuthenticatedUser OFFICER = new AuthenticatedUser(7L, "officer@chubb.com", "Demo Officer", Role.OFFICER);
    private static final AuthenticatedUser ADMIN = new AuthenticatedUser(8L, "admin@chubb.com", "Demo Admin", Role.ADMIN);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtService jwtService;

    // ---- GET /api/notifications -------------------------------------------------------------------------------

    @Test
    void theInboxIsAPageOfNotificationsNewestFirstTwentyPerPage() throws Exception {
        when(notificationService.getNotifications(eq(CLAIMANT), eq(false), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/notifications").with(as(CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].claimId").value(100))
                .andExpect(jsonPath("$.content[0].claimNumber").value("CLM-2026-000100"))
                .andExpect(jsonPath("$.content[0].eventType").value("CLAIM_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].message").value("Your claim CLM-2026-000100 has been assigned."))
                .andExpect(jsonPath("$.content[0].read").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").value("2026-10-22T06:00:00Z"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.last").value(true));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(eq(CLAIMANT), eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"));
    }

    @Test
    void theUnreadFilterPagingAndSortArePassedThrough() throws Exception {
        when(notificationService.getNotifications(any(), eq(true), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/notifications").with(as(CLAIMANT))
                        .param("unreadOnly", "true").param("page", "2").param("size", "5").param("sort", "createdAt,asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(eq(CLAIMANT), eq(true), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort()).containsExactly(Sort.Order.asc("createdAt"));
    }

    @Test
    void thePageSizeIsCappedAt100() throws Exception {
        when(notificationService.getNotifications(any(), eq(false), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/notifications").with(as(CLAIMANT)).param("size", "100000")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(any(), eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void anUnsupportedSortFieldIs400() throws Exception {
        when(notificationService.getNotifications(any(), eq(false), any(Pageable.class)))
                .thenThrow(new InvalidRequestException("Unsupported sort field 'message'; allowed fields: [createdAt]"));

        mockMvc.perform(get("/api/notifications").with(as(CLAIMANT)).param("sort", "message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported sort field 'message'; allowed fields: [createdAt]"));
    }

    @Test
    void aNonBooleanUnreadOnlyIs400() throws Exception {
        mockMvc.perform(get("/api/notifications").with(as(CLAIMANT)).param("unreadOnly", "maybe"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(notificationService);
    }

    // ---- GET /api/notifications/unread-count ------------------------------------------------------------------

    @Test
    void theUnreadCountIsAnObjectForTheBadge() throws Exception {
        when(notificationService.countUnread(CLAIMANT)).thenReturn(new UnreadCountResponse(3));

        mockMvc.perform(get("/api/notifications/unread-count").with(as(CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    // ---- POST /api/notifications/{id}/read --------------------------------------------------------------------

    @Test
    void markingOneReadReturnsItRead() throws Exception {
        when(notificationService.markRead(10L, CLAIMANT)).thenReturn(item(true));

        mockMvc.perform(post("/api/notifications/10/read").with(as(CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markingAnUnknownOrForeignNotificationIs404AsJson() throws Exception {
        when(notificationService.markRead(404L, CLAIMANT)).thenThrow(new NotificationNotFoundException(404L));

        mockMvc.perform(post("/api/notifications/404/read").with(as(CLAIMANT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Notification '404' was not found"))
                .andExpect(jsonPath("$.path").value("/api/notifications/404/read"));
    }

    @Test
    void aNonNumericNotificationIdIs400() throws Exception {
        mockMvc.perform(post("/api/notifications/abc/read").with(as(CLAIMANT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(notificationService);
    }

    // ---- POST /api/notifications/read-all ---------------------------------------------------------------------

    @Test
    void markAllReadReportsHowManyWereUnread() throws Exception {
        when(notificationService.markAllRead(CLAIMANT)).thenReturn(new MarkedReadResponse(4));

        mockMvc.perform(post("/api/notifications/read-all").with(as(CLAIMANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedRead").value(4));
    }

    // ---- who may call -----------------------------------------------------------------------------------------

    @Test
    void everyRoleHasAnInbox() throws Exception {
        when(notificationService.getNotifications(any(), eq(false), any(Pageable.class))).thenReturn(onePage());
        when(notificationService.countUnread(any())).thenReturn(new UnreadCountResponse(0));
        when(notificationService.markAllRead(any())).thenReturn(new MarkedReadResponse(0));

        for (AuthenticatedUser user : List.of(CLAIMANT, OFFICER, ADMIN)) {
            mockMvc.perform(get("/api/notifications").with(as(user))).andExpect(status().isOk());
            mockMvc.perform(get("/api/notifications/unread-count").with(as(user))).andExpect(status().isOk());
            mockMvc.perform(post("/api/notifications/read-all").with(as(user))).andExpect(status().isOk());
        }
        // Each call is made for the caller's own identity, never one supplied by the client.
        verify(notificationService).getNotifications(eq(OFFICER), eq(false), any(Pageable.class));
        verify(notificationService).countUnread(ADMIN);
    }

    @Test
    void everyNotificationEndpointNeedsAToken() throws Exception {
        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/notifications"),
                get("/api/notifications/unread-count"),
                post("/api/notifications/10/read"),
                post("/api/notifications/read-all"))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }
        verifyNoInteractions(notificationService);
    }

    // ---- helpers ----------------------------------------------------------------------------------------------

    private static RequestPostProcessor as(AuthenticatedUser user) {
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
    }

    private static NotificationResponse item(boolean read) {
        return new NotificationResponse(10L, 100L, "CLM-2026-000100", ClaimEventType.CLAIM_ASSIGNED,
                "Your claim CLM-2026-000100 has been assigned.", read, NOW);
    }

    private static PageResponse<NotificationResponse> onePage() {
        return new PageResponse<>(List.of(item(false)), 0, 20, 1, 1, true, true);
    }
}
