package com.chubb.claims.claim.entity;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketTest {

    @Test
    void everyMarketHasItsOwnCurrencyAndEveryCurrencyBelongsToAMarket() {
        assertThat(Arrays.stream(Market.values()).map(Market::currency)).doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(Currency.values());
    }

    @Test
    void theCurrenciesAreTheOnesTheRateCardAndTheSeedDataAssume() {
        Map<Market, Currency> expected = new EnumMap<>(Market.class);
        expected.put(Market.AU, Currency.AUD);
        expected.put(Market.HK, Currency.HKD);
        expected.put(Market.JP, Currency.JPY);
        expected.put(Market.MY, Currency.MYR);
        expected.put(Market.NZ, Currency.NZD);
        expected.put(Market.SG, Currency.SGD);

        assertThat(Arrays.stream(Market.values())).allSatisfy(market -> assertThat(market.currency()).isEqualTo(expected.get(market)));
    }
}
