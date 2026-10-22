package com.chubb.claims.claim.controller;

import com.chubb.claims.claim.dto.ErrorResponse;
import com.chubb.claims.claim.dto.MarkedReadResponse;
import com.chubb.claims.claim.dto.NotificationResponse;
import com.chubb.claims.claim.dto.PageResponse;
import com.chubb.claims.claim.dto.UnreadCountResponse;
import com.chubb.claims.claim.security.AuthenticatedUser;
import com.chubb.claims.claim.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "The caller's own in-app notifications: inbox, unread badge, mark as read")
public class NotificationController {

    /** Stated on the annotation: its own default (10) would otherwise win over any property. */
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List my notifications",
            description = "Any signed-in user; you only ever see your own. Newest first by default; the only sortable field "
                    + "is `createdAt` (`sort=createdAt,asc|desc`). Pages are zero-based, 20 per page, at most 100. "
                    + "Pass `unreadOnly=true` for just the unread ones.")
    @ApiResponse(responseCode = "400", description = "Unsupported sort field",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.getNotifications(user, unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count my unread notifications", description = "The number to show on the notification badge.")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.countUnread(user);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read",
            description = "Idempotent: an already-read notification is returned unchanged.")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No such notification, or it belongs to someone else",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.markRead(id, user);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all my notifications as read",
            description = "Reports how many were unread; 0 when there was nothing to do.")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public MarkedReadResponse markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return notificationService.markAllRead(user);
    }
}
