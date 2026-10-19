package com.sklum.backend.model;

/**
 * {@code simulateFailure} is the fault-injection flag from CLAUDE.md's "Saga demo mechanism"
 * decision: one of "reserve", "payment", "confirm", or null/absent for a normal run. It is
 * forwarded as a query flag to the one specific mock-backends call that should fail, so a
 * reviewer can trigger and observe the exact compensation path on demand.
 */
public record CheckoutRequest(
        String sku,
        int quantity,
        String postalCode,
        String simulateFailure
) {}
