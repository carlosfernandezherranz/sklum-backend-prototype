package com.sklum.backend.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Two carriers, two independent circuit breakers ("carrierA"/"carrierB") — one partner being
 * slow or down should not affect calls to the other, and PromisePrecomputeJob's "pick the
 * fastest" logic needs to know when a carrier's number is real vs. a fallback placeholder.
 */
@Component
public class CarrierClient {

    private static final Logger log = LoggerFactory.getLogger(CarrierClient.class);

    private final RestClient client;

    public CarrierClient(RestClient mockBackendsClient) {
        this.client = mockBackendsClient;
    }

    @CircuitBreaker(name = "carrierA", fallbackMethod = "etaAFallback")
    @Retry(name = "carrierRead")
    public CarrierEta etaCarrierA(String warehouseId, String zone) {
        Map<?, ?> body = client.get()
                .uri(uriBuilder -> uriBuilder.path("/carrier/a/eta")
                        .queryParam("warehouseId", warehouseId).queryParam("zone", zone).build())
                .retrieve()
                .body(Map.class);
        return new CarrierEta("CARRIER-A", ((Number) body.get("minDays")).intValue(),
                ((Number) body.get("maxDays")).intValue(), (Boolean) body.get("capacityOk"), true);
    }

    @SuppressWarnings("unused")
    private CarrierEta etaAFallback(String warehouseId, String zone, Throwable t) {
        log.warn("carrier A degraded for {}/{}: {}", warehouseId, zone, t.toString());
        return new CarrierEta("CARRIER-A", 0, 0, false, false);
    }

    @CircuitBreaker(name = "carrierB", fallbackMethod = "etaBFallback")
    @Retry(name = "carrierRead")
    public CarrierEta etaCarrierB(String warehouseId, String zone) {
        Map<?, ?> body = client.get()
                .uri(uriBuilder -> uriBuilder.path("/carrier/b/eta")
                        .queryParam("warehouseId", warehouseId).queryParam("zone", zone).build())
                .retrieve()
                .body(Map.class);
        return new CarrierEta("CARRIER-B", ((Number) body.get("minDays")).intValue(),
                ((Number) body.get("maxDays")).intValue(), (Boolean) body.get("capacityOk"), true);
    }

    @SuppressWarnings("unused")
    private CarrierEta etaBFallback(String warehouseId, String zone, Throwable t) {
        log.warn("carrier B degraded for {}/{}: {}", warehouseId, zone, t.toString());
        return new CarrierEta("CARRIER-B", 0, 0, false, false);
    }
}
