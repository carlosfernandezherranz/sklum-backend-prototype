package com.sklum.backend.model;

import java.time.LocalDateTime;

public record DeliverySummary(
        String orderId,
        String sku,
        String status,
        Integer promiseMinDays,
        Integer promiseMaxDays,
        LocalDateTime createdAt
) {}
