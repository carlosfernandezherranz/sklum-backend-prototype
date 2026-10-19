package com.sklum.backend.service;

import com.sklum.backend.model.SearchResponse;
import com.sklum.backend.observability.OpsSummaryService;
import com.sklum.backend.repository.ProductRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final ProductRepository productRepository;
    private final Timer searchTimer;

    public SearchService(ProductRepository productRepository, MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.searchTimer = Timer.builder(OpsSummaryService.SEARCH_TIMER)
                .description("Backend /search endpoint latency")
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry);
    }

    public SearchResponse search(String query, int limit) {
        return searchTimer.record(() -> {
            var results = productRepository.search(query, limit);
            return new SearchResponse(query, results.size(), results);
        });
    }
}
