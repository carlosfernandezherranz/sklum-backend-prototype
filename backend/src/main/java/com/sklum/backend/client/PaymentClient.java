package com.sklum.backend.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Adapter to the mock Payment provider. No retry on charge/refund — both are non-idempotent
 * from the caller's side (no idempotency key in this prototype), so a transient failure is
 * surfaced to OrderSagaOrchestrator as a failed step rather than silently retried, which is
 * exactly the behaviour the saga's compensation logic is built to handle.
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    private final RestClient client;

    public PaymentClient(RestClient mockBackendsClient) {
        this.client = mockBackendsClient;
    }

    public record ChargeResult(boolean success, String chargeId, String error) {}

    @CircuitBreaker(name = "payment", fallbackMethod = "chargeFallback")
    public ChargeResult charge(String orderId, long amountCents, boolean simulateFailure) {
        try {
            Map<?, ?> body = client.post()
                    .uri(uriBuilder -> uriBuilder.path("/payment/charges")
                            .queryParam("simulateFailure", simulateFailure).build())
                    .body(Map.of("orderId", orderId, "amountCents", amountCents))
                    .retrieve()
                    .body(Map.class);
            return new ChargeResult(true, String.valueOf(body.get("chargeId")), null);
        } catch (RestClientResponseException e) {
            log.warn("charge failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ChargeResult(false, null, "charge failed: HTTP " + e.getStatusCode().value());
        }
    }

    @SuppressWarnings("unused")
    private ChargeResult chargeFallback(String orderId, long amountCents, boolean simulateFailure, Throwable t) {
        log.warn("payment circuit open for order={}: {}", orderId, t.toString());
        return new ChargeResult(false, null, "payment unavailable (circuit open): " + t.getMessage());
    }

    @CircuitBreaker(name = "payment", fallbackMethod = "refundFallback")
    public void refund(String chargeId) {
        try {
            client.post().uri("/payment/charges/{id}/refund", chargeId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("compensating refund failed for {}: {}", chargeId, e.getStatusCode());
        }
    }

    @SuppressWarnings("unused")
    private void refundFallback(String chargeId, Throwable t) {
        log.error("compensating refund COULD NOT BE SENT (circuit open) for charge={}: {}", chargeId, t.toString());
    }
}
