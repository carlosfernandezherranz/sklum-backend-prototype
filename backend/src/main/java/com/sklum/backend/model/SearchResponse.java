package com.sklum.backend.model;

import java.util.List;

public record SearchResponse(String query, int resultCount, List<ProductSummary> results) {}
