package com.sklum.backend.observability;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Ops", description = "Search latency, promise cache, saga outcomes, circuit breaker states")
public class OpsController {

    private final OpsSummaryService summaryService;

    public OpsController(OpsSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @Operation(summary = "Search latency, promise cache hit rate, saga outcome counts, circuit breaker states")
    @GetMapping("/ops/summary")
    public OpsSummary summary() {
        return summaryService.summarize();
    }
}
