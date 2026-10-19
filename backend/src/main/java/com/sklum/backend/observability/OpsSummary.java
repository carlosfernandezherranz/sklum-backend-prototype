package com.sklum.backend.observability;

import java.util.Map;

public record OpsSummary(
        double searchP50Ms,
        double searchP95Ms,
        long searchRequestCount,
        long promiseCacheHits,
        long promiseCacheMisses,
        int promiseLookupRows,
        Map<String, Integer> orderStatusCounts,
        Map<String, Integer> sagaStepCounts,
        Map<String, String> circuitBreakerStates
) {}
