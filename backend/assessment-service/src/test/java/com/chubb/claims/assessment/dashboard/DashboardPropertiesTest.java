package com.chubb.claims.assessment.dashboard;

import com.chubb.claims.assessment.config.DashboardProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardPropertiesTest {

    @Test
    void acceptsAReportingCurrencyAndPositiveRates() {
        DashboardProperties properties = new DashboardProperties("USD", Map.of("SGD", new BigDecimal("1.35")));

        assertThat(properties.reportingCurrency()).isEqualTo("USD");
        assertThat(properties.rates()).containsEntry("SGD", new BigDecimal("1.35"));
    }

    @Test
    void ratesAreOptionalUntilAConversionNeedsOne() {
        assertThat(new DashboardProperties("USD", null).rates()).isEmpty();
    }

    @Test
    void theReportingCurrencyMustBeAnIsoCode() {
        for (String bad : new String[]{null, "", "usd", "US", "DOLLAR"}) {
            assertThatThrownBy(() -> new DashboardProperties(bad, Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("reporting-currency");
        }
    }

    @Test
    void aRateMustBeGreaterThanZero() {
        assertThatThrownBy(() -> new DashboardProperties("USD", Map.of("JPY", BigDecimal.ZERO)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JPY");
        assertThatThrownBy(() -> new DashboardProperties("USD", Map.of("JPY", new BigDecimal("-150"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JPY");
    }
}
