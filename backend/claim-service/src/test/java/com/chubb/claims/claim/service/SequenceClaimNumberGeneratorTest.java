package com.chubb.claims.claim.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SequenceClaimNumberGeneratorTest {

    private static final String NEXT_VALUE_SQL = "SELECT NEXT VALUE FOR claim_number_seq";

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SequenceClaimNumberGenerator generatorIn(String instant) {
        return new SequenceClaimNumberGenerator(jdbcTemplate, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test
    void formatsYearAndZeroPaddedSequence() {
        when(jdbcTemplate.queryForObject(NEXT_VALUE_SQL, Long.class)).thenReturn(42L);

        assertThat(generatorIn("2026-10-22T06:00:00Z").next()).isEqualTo("CLM-2026-000042");
    }

    @Test
    void usesTheYearOfTheClock() {
        when(jdbcTemplate.queryForObject(NEXT_VALUE_SQL, Long.class)).thenReturn(1L);

        assertThat(generatorIn("2027-01-01T00:00:00Z").next()).isEqualTo("CLM-2027-000001");
    }

    @Test
    void neverTruncatesLargeSequenceValues() {
        when(jdbcTemplate.queryForObject(NEXT_VALUE_SQL, Long.class)).thenReturn(1_234_567L);

        assertThat(generatorIn("2026-10-22T06:00:00Z").next()).isEqualTo("CLM-2026-1234567");
    }
}
