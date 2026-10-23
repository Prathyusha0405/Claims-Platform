package com.chubb.claims.assessment.service;

import com.chubb.claims.assessment.config.DashboardProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Converts a claim amount into the dashboards' reporting currency at the configured, fixed, indicative rates. */
@Component
public class ExchangeRates {

    private static final int MONEY_SCALE = 2;

    private final String reportingCurrency;
    private final Map<String, BigDecimal> unitsPerReportingUnit;

    public ExchangeRates(DashboardProperties properties) {
        this.reportingCurrency = properties.reportingCurrency();
        this.unitsPerReportingUnit = properties.rates();
    }

    public String reportingCurrency() {
        return reportingCurrency;
    }

    /** Units of {@code currency} that buy one unit of the reporting currency. */
    public BigDecimal rate(String currency) {
        if (reportingCurrency.equals(currency)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = currency == null ? null : unitsPerReportingUnit.get(currency);
        if (rate == null) {
            throw new IllegalStateException("No exchange rate is configured for " + currency + " (app.dashboard.rates)");
        }
        return rate;
    }

    /** {@code amount} of {@code currency}, in the reporting currency, rounded half up to cents. */
    public BigDecimal toReportingCurrency(String currency, BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE);
        }
        return amount.divide(rate(currency), MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
