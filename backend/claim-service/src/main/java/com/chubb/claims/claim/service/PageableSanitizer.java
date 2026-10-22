package com.chubb.claims.claim.service;

import com.chubb.claims.claim.exception.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/** Keeps client-supplied paging safe: only allow-listed sort fields reach the database layer, and the order is stable. */
final class PageableSanitizer {

    private PageableSanitizer() {
    }

    /**
     * Rejects sort fields outside {@code sortableFields} and makes the order deterministic: newest first by default
     * ({@code createdAt}, which every sortable entity has), with the id as a tie-breaker so rows with equal sort
     * values never shuffle between pages.
     */
    static Pageable sanitize(Pageable pageable, Set<String> sortableFields) {
        for (Sort.Order order : pageable.getSort()) {
            if (!sortableFields.contains(order.getProperty())) {
                throw new InvalidRequestException("Unsupported sort field '" + order.getProperty()
                        + "'; allowed fields: " + new TreeSet<>(sortableFields));
            }
        }

        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(sort.iterator().next().getDirection(), "id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
