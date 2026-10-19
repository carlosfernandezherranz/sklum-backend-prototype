package com.sklum.backend.repository;

public record PromiseRow(
        String sku,
        String warehouseId,
        String granularity,
        String zoneKey,
        String carrierId,
        int minDays,
        int maxDays
) {}
