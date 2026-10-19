package com.sklum.backend.model;

/**
 * A delivery-promise date range (never an exact date — locked in the business case).
 * {@code granularity} and {@code source} are there so a reviewer can see, not just trust,
 * whether this came from the coarse zone-level precompute or the finer postcode-level one,
 * and whether a live inventory check adjusted it at checkout.
 */
public record PromiseWindow(
        int minDays,
        int maxDays,
        String carrierId,
        String warehouseId,
        String granularity,
        String source
) {}
