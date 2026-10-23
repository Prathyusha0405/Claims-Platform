package com.chubb.claims.claim.entity;

/**
 * The markets Chubb APAC writes motor and property business in, by ISO 3166-1 alpha-2 country code. Each market has
 * one currency, and a claim is always in the currency of the market it is filed in.
 */
public enum Market {
    AU(Currency.AUD),
    HK(Currency.HKD),
    JP(Currency.JPY),
    MY(Currency.MYR),
    NZ(Currency.NZD),
    SG(Currency.SGD);

    private final Currency currency;

    Market(Currency currency) {
        this.currency = currency;
    }

    /** The currency every claim filed in this market is made in. */
    public Currency currency() {
        return currency;
    }
}
