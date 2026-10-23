package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.config.DashboardProperties;
import com.chubb.claims.assessment.service.ExchangeRates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRatesTest {

    private final ExchangeRates rates = new ExchangeRates(new DashboardProperties("USD",
            Map.of("SGD", new BigDecimal("1.35"), "JPY", new BigDecimal("150"), "NZD", new BigDecimal("1.65"))));

    @Test
    void reportsInTheConfiguredCurrency() {
        assertThat(rates.reportingCurrency()).isEqualTo("USD");
    }

    @Test
    void theReportingCurrencyNeedsNoRate() {
        assertThat(rates.rate("USD")).isEqualByComparingTo("1");
        assertThat(rates.toReportingCurrency("USD", new BigDecimal("12.34"))).isEqualByComparingTo("12.34");
    }

    @Test
    void dividesByTheRateAndRoundsHalfUpToCents() {
        assertThat(rates.toReportingCurrency("SGD", new BigDecimal("135.00"))).isEqualByComparingTo("100.00");
        assertThat(rates.toReportingCurrency("JPY", new BigDecimal("1000"))).isEqualByComparingTo("6.67");   // 6.666...
        assertThat(rates.toReportingCurrency("NZD", new BigDecimal("1.00"))).isEqualByComparingTo("0.61");   // 0.606...
        assertThat(rates.toReportingCurrency("JPY", new BigDecimal("1000")).scale()).isEqualTo(2);
    }

    @Test
    void anAbsentAmountIsZero() {
        assertThat(rates.toReportingCurrency("SGD", null)).isEqualByComparingTo("0.00");
    }

    @Test
    void aCurrencyWithoutARateIsRefused() {
        assertThatThrownBy(() -> rates.rate("MYR")).isInstanceOf(IllegalStateException.class).hasMessageContaining("MYR");
        assertThatThrownBy(() -> rates.rate(null)).isInstanceOf(IllegalStateException.class);
    }
}
