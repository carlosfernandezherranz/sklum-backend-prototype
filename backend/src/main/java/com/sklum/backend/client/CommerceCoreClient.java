package com.sklum.backend.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * The one adapter that knows how to talk to the mock Commerce Core — shared by
 * DeliveryPromiseService (live inventory check), OrderSagaOrchestrator (reserve/confirm),
 * and PromisePrecomputeJob (inventory snapshot). See Fig. 4's rationale for why this is a
 * single shared component rather than three separate clients.
 *
 * Every call is behind the "commerceCore" circuit breaker (config in application.yml). Reads
 * (inventory) also retry on transient failure, since a GET is safe to repeat; writes
 * (reserve/release/confirm) do not retry automatically — retrying a non-idempotent call is
 * exactly the kind of thing that turns "one failed reservation" into "two reservations", so
 * that risk is left to the saga's own explicit compensation instead.
 */
@Component
public class CommerceCoreClient {

    private static final Logger log = LoggerFactory.getLogger(CommerceCoreClient.class);

    private final RestClient client;

    public CommerceCoreClient(RestClient mockBackendsClient) {
        this.client = mockBackendsClient;
    }

    @CircuitBreaker(name = "commerceCore", fallbackMethod = "inventoryFallback")
    @Retry(name = "commerceCoreRead")
    public List<InventoryRow> inventory(String sku) {
        return client.get()
                .uri("/commerce-core/inventory/{sku}", sku)
                .retrieve()
                .body(new ParameterizedTypeReference<List<InventoryRow>>() {});
    }

    // Degraded-state fallback for the read path: an empty inventory view rather than a thrown
    // exception, so DeliveryPromiseService/PromisePrecomputeJob can fall back to "no live signal,
    // use the last precomputed promise" instead of the request failing outright.
    @SuppressWarnings("unused")
    private List<InventoryRow> inventoryFallback(String sku, Throwable t) {
        log.warn("commerce-core inventory degraded for sku={}: {}", sku, t.toString());
        return List.of();
    }

    public record ReserveResult(boolean success, String reservationId, String error) {}

    @CircuitBreaker(name = "commerceCore", fallbackMethod = "reserveFallback")
    public ReserveResult reserve(String orderId, String sku, String warehouseId, int quantity, boolean simulateFailure) {
        try {
            Map<?, ?> body = client.post()
                    .uri(uriBuilder -> uriBuilder.path("/commerce-core/reservations")
                            .queryParam("simulateFailure", simulateFailure).build())
                    .body(Map.of("orderId", orderId, "sku", sku, "warehouseId", warehouseId, "quantity", quantity))
                    .retrieve()
                    .body(Map.class);
            return new ReserveResult(true, String.valueOf(body.get("reservationId")), null);
        } catch (RestClientResponseException e) {
            log.warn("reserve failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ReserveResult(false, null, "reserve failed: HTTP " + e.getStatusCode().value());
        }
    }

    @SuppressWarnings("unused")
    private ReserveResult reserveFallback(String orderId, String sku, String warehouseId, int quantity,
                                           boolean simulateFailure, Throwable t) {
        log.warn("commerce-core reserve circuit open for order={}: {}", orderId, t.toString());
        return new ReserveResult(false, null, "commerce-core unavailable (circuit open): " + t.getMessage());
    }

    @CircuitBreaker(name = "commerceCore", fallbackMethod = "releaseFallback")
    public void release(String reservationId) {
        try {
            client.post().uri("/commerce-core/reservations/{id}/release", reservationId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.warn("compensating release failed for {}: {}", reservationId, e.getStatusCode());
        }
    }

    @SuppressWarnings("unused")
    private void releaseFallback(String reservationId, Throwable t) {
        // Best-effort compensation: circuit is open, so Commerce Core is already known to be
        // unhealthy. Logged loudly rather than retried blindly — an un-released reservation is
        // exactly the kind of drift the saga's audit log (saga_steps) exists to surface.
        log.error("compensating release COULD NOT BE SENT (circuit open) for reservation={}: {}", reservationId, t.toString());
    }

    public record ConfirmResult(boolean success, String error) {}

    @CircuitBreaker(name = "commerceCore", fallbackMethod = "confirmFallback")
    public ConfirmResult confirm(String orderId, boolean simulateFailure) {
        try {
            client.post()
                    .uri(uriBuilder -> uriBuilder.path("/commerce-core/orders/{orderId}/confirm")
                            .queryParam("simulateFailure", simulateFailure).build(orderId))
                    .retrieve()
                    .toBodilessEntity();
            return new ConfirmResult(true, null);
        } catch (RestClientResponseException e) {
            log.warn("confirm failed: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            return new ConfirmResult(false, "confirm failed: HTTP " + e.getStatusCode().value());
        }
    }

    @SuppressWarnings("unused")
    private ConfirmResult confirmFallback(String orderId, boolean simulateFailure, Throwable t) {
        log.warn("commerce-core confirm circuit open for order={}: {}", orderId, t.toString());
        return new ConfirmResult(false, "commerce-core unavailable (circuit open): " + t.getMessage());
    }
}
