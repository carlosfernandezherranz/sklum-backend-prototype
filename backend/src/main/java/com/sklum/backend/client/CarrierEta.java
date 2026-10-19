package com.sklum.backend.client;

/** {@code available=false} means this came from a circuit-breaker fallback, not a real carrier response. */
public record CarrierEta(String carrierId, int minDays, int maxDays, boolean capacityOk, boolean available) {}
