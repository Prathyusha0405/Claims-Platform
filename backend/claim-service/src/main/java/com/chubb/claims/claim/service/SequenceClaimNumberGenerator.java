package com.chubb.claims.claim.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/** Numbers claims from the {@code claim_number_seq} database sequence (created by Flyway), so numbers never repeat. */
@Component
public class SequenceClaimNumberGenerator implements ClaimNumberGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SequenceClaimNumberGenerator(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public String next() {
        Long sequenceValue = jdbcTemplate.queryForObject("SELECT NEXT VALUE FOR claim_number_seq", Long.class);
        return "CLM-%d-%06d".formatted(Year.now(clock).getValue(), sequenceValue);
    }
}
