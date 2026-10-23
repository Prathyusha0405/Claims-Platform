package com.chubb.claims.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * How the dashboards report money. Claims are in their own market's currency, so a single exposure figure needs a
 * conversion: this is the currency it is reported in and the rate card used to get there.
 *
 * @param reportingCurrency ISO 4217 code the dashboards report in, e.g. {@code USD}
 * @param rates             units of each currency that buy one unit of the reporting currency (so {@code JPY: 150}
 *                          means 150 yen to one dollar). Indicative and fixed, not live market rates.
 */
@ConfigurationProperties(prefix = "app.dashboard")
public record DashboardProperties(String reportingCurrency, Map<String, BigDecimal> rates) {

    public DashboardProperties {
        if (reportingCurrency == null || !reportingCurrency.matches("[A-Z]{3}")) {
            throw new IllegalStateException("app.dashboard.reporting-currency must be an ISO 4217 code such as USD");
        }
        rates = rates == null ? Map.of() : Map.copyOf(rates);
        rates.forEach((currency, rate) -> {
            if (rate == null || rate.signum() <= 0) {
                throw new IllegalStateException("app.dashboard.rates." + currency + " must be greater than zero");
            }
        });
    }
}
