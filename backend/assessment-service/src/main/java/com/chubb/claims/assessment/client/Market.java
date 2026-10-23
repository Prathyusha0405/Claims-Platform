package com.chubb.claims.assessment.client;

/**
 * The markets claim-service knows, by ISO country code. Used to check the `market` parameter of the dashboards before
 * claim-service is asked; claim-service owns the list and the currency of each market.
 */
public enum Market {
    AU,
    HK,
    JP,
    MY,
    NZ,
    SG
}
