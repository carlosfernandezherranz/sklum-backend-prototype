package com.sklum.backend.web;

import com.sklum.backend.model.CheckoutRequest;
import com.sklum.backend.model.CheckoutResponse;
import com.sklum.backend.service.OrderSagaOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Checkout", description = "Fine-grained promise + live inventory check, then the order saga")
public class CheckoutController {

    private final OrderSagaOrchestrator sagaOrchestrator;

    public CheckoutController(OrderSagaOrchestrator sagaOrchestrator) {
        this.sagaOrchestrator = sagaOrchestrator;
    }

    @Operation(summary = "Place an order: Reserve -> Charge -> Confirm, with compensation on failure",
            description = "Set simulateFailure to \"reserve\", \"payment\", or \"confirm\" to force that " +
                    "step to fail and observe the resulting compensation. Pass Idempotency-Key to safely " +
                    "retry the same request (e.g. after a client-side timeout) without reserving/charging " +
                    "twice — a repeated key replays the first call's result instead of re-running the saga.")
    @PostMapping("/checkout")
    public CheckoutResponse checkout(@RequestBody CheckoutRequest request,
                                      @Parameter(description = "Optional — same key on a retry replays the original result instead of re-running the saga")
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return sagaOrchestrator.checkout(request, idempotencyKey);
    }
}
