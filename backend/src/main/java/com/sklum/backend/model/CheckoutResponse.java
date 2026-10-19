package com.sklum.backend.model;

import java.util.List;

public record CheckoutResponse(
        String orderId,
        String status,
        PromiseWindow promise,
        List<String> sagaSteps,
        String message
) {}
