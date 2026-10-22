package com.chubb.claims.assessment.dto;

import com.chubb.claims.assessment.client.RemotePage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

/** Same paged JSON shape as claim-service. */
public record PageResponse<T>(
        List<T> content,
        @Schema(description = "Zero-based page index") int page,
        @Schema(description = "Requested page size") int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <S, T> PageResponse<T> from(RemotePage<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.content().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.first(),
                page.last());
    }
}
