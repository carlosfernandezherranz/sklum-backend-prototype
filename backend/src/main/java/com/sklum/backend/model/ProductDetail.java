package com.sklum.backend.model;

public record ProductDetail(
        String sku,
        String name,
        String category,
        String description,
        int priceCents,
        String currency
) {}
