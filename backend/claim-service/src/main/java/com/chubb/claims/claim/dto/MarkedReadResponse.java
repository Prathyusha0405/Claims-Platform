package com.chubb.claims.claim.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Result of "mark all as read". */
public record MarkedReadResponse(
        @Schema(description = "How many notifications were unread and are now read; 0 when there was nothing to do")
        long markedRead) {
}
