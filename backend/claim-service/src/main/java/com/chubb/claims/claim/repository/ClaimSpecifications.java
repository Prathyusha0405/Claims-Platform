package com.chubb.claims.claim.repository;

import com.chubb.claims.claim.dto.ClaimSearchCriteria;
import com.chubb.claims.claim.entity.Claim;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the dynamic WHERE clause for claim search; every supplied filter is ANDed. */
public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    /**
     * @param restrictToClaimantId when non-null, results are limited to that claimant's own claims
     */
    public static Specification<Claim> matching(ClaimSearchCriteria criteria, Long restrictToClaimantId) {
        List<Specification<Claim>> filters = new ArrayList<>();

        if (restrictToClaimantId != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("claimantId"), restrictToClaimantId));
        }
        if (StringUtils.hasText(criteria.claimNumber())) {
            filters.add(containsIgnoreCase("claimNumber", criteria.claimNumber()));
        }
        if (StringUtils.hasText(criteria.policyNumber())) {
            filters.add(containsIgnoreCase("policyNumber", criteria.policyNumber()));
        }
        if (criteria.status() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), criteria.status()));
        }
        if (criteria.assignedOfficerId() != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("assignedOfficerId"), criteria.assignedOfficerId()));
        }
        if (criteria.fromDate() != null) {
            Instant start = startOfDay(criteria.fromDate());
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), start));
        }
        if (criteria.toDate() != null) {
            // Inclusive of the whole "to" day: strictly before the start of the next day.
            Instant endExclusive = startOfDay(criteria.toDate().plusDays(1));
            filters.add((root, query, cb) -> cb.lessThan(root.<Instant>get("createdAt"), endExclusive));
        }

        return Specification.allOf(filters);
    }

    private static Specification<Claim> containsIgnoreCase(String attribute, String value) {
        String pattern = "%" + escapeLike(value.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(attribute)), pattern, '\\');
    }

    /** User input must not act as LIKE wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
