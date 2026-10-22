package com.chubb.claims.assessment.client;

import java.util.List;

/** A page as claim-service returns it (its PageResponse). */
public record RemotePage<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {
}
