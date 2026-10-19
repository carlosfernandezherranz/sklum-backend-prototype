package com.sklum.backend.web;

import com.sklum.backend.model.SearchResponse;
import com.sklum.backend.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Search", description = "Postgres trigram search over the catalog")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "Fuzzy/typo-tolerant product search (pg_trgm, not Elasticsearch)")
    @GetMapping("/search")
    public SearchResponse search(@RequestParam("q") String query,
                                  @RequestParam(defaultValue = "20") int limit) {
        return searchService.search(query, limit);
    }
}
