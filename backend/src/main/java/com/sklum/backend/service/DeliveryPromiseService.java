package com.sklum.backend.service;

import com.sklum.backend.client.CommerceCoreClient;
import com.sklum.backend.model.PromiseWindow;
import com.sklum.backend.observability.OpsSummaryService;
import com.sklum.backend.repository.PromiseLookupRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

/**
 * The two-tier delivery promise from Fig. 3/Fig. 5: PDP reads a coarse, precomputed,
 * zone-level window; checkout reads a finer precomputed window plus one live inventory
 * check — never a live carrier call. Both reads are cheap; only checkout pays the extra
 * live-check cost, and only for the one thing (stock) that can genuinely invalidate the
 * promise between precompute and purchase.
 */
@Service
public class DeliveryPromiseService {

    private static final String DEFAULT_DEMO_POSTAL_CODE = "28013"; // PDP has no address yet — see pdpPromise()

    private final PromiseLookupRepository promiseLookupRepository;
    private final CommerceCoreClient commerceCoreClient;
    private final Counter cacheHit;
    private final Counter cacheMiss;

    public DeliveryPromiseService(PromiseLookupRepository promiseLookupRepository,
                                   CommerceCoreClient commerceCoreClient,
                                   MeterRegistry meterRegistry) {
        this.promiseLookupRepository = promiseLookupRepository;
        this.commerceCoreClient = commerceCoreClient;
        this.cacheHit = Counter.builder(OpsSummaryService.PROMISE_CACHE_COUNTER).tag("result", "hit")
                .description("promise_lookup reads that found a precomputed row").register(meterRegistry);
        this.cacheMiss = Counter.builder(OpsSummaryService.PROMISE_CACHE_COUNTER).tag("result", "miss")
                .description("promise_lookup reads that fell back or found nothing").register(meterRegistry);
    }

    public static String zoneOf(String postalCode) {
        String digits = postalCode.replaceAll("\\D", "");
        return digits.length() >= 2 ? digits.substring(0, 2) : digits;
    }

    public static String postcodeKeyOf(String postalCode) {
        String digits = postalCode.replaceAll("\\D", "");
        return digits.length() >= 5 ? digits.substring(0, 5) : digits;
    }

    /**
     * No address at PDP time, so this uses a representative default postal zone — in
     * production this would come from the customer's last known address or IP geolocation;
     * flagged here rather than quietly hard-coded.
     */
    public PromiseWindow pdpPromise(String sku, String postalCodeOrNull) {
        String effectivePostal = (postalCodeOrNull == null || postalCodeOrNull.isBlank())
                ? DEFAULT_DEMO_POSTAL_CODE : postalCodeOrNull;
        String zone = zoneOf(effectivePostal);
        var row = promiseLookupRepository.findBestZone(sku, zone);
        if (row.isEmpty()) {
            cacheMiss.increment();
            return new PromiseWindow(3, 7, "UNKNOWN", null, "ZONE", "no precomputed promise yet — default range");
        }
        cacheHit.increment();
        var r = row.get();
        return new PromiseWindow(r.minDays(), r.maxDays(), r.carrierId(), r.warehouseId(), "ZONE", "precomputed (PDP)");
    }

    public PromiseWindow checkoutPromise(String sku, String postalCode) {
        String postcodeKey = postcodeKeyOf(postalCode);
        String zone = zoneOf(postalCode);

        var fine = promiseLookupRepository.findBestPostcode(sku, postcodeKey);
        PromiseWindow base;
        if (fine.isPresent()) {
            cacheHit.increment();
            var r = fine.get();
            base = new PromiseWindow(r.minDays(), r.maxDays(), r.carrierId(), r.warehouseId(),
                    "POSTCODE", "precomputed (checkout, fine-grained)");
        } else {
            cacheMiss.increment();
            var zoneRow = promiseLookupRepository.findBestZone(sku, zone);
            if (zoneRow.isEmpty()) {
                return new PromiseWindow(3, 7, "UNKNOWN", null, "ZONE", "no precomputed promise — default range");
            }
            var r = zoneRow.get();
            base = new PromiseWindow(r.minDays(), r.maxDays(), r.carrierId(), r.warehouseId(),
                    "ZONE", "fell back to zone-level (no fine-grained entry for this postcode)");
        }

        // The one live call in the whole delivery-promise path — confirms the chosen
        // warehouse still has stock. If the circuit to Commerce Core is open, inventory()
        // degrades to an empty list rather than throwing, and we simply skip this check.
        var liveStock = commerceCoreClient.inventory(sku);
        boolean stillInStock = liveStock.stream()
                .anyMatch(r -> r.warehouseId().equals(base.warehouseId()) && r.stockLevel() > 0);
        if (!liveStock.isEmpty() && !stillInStock) {
            return new PromiseWindow(base.minDays(), base.maxDays() + 3, base.carrierId(), base.warehouseId(),
                    base.granularity(), "live check: warehouse stock changed since precompute — widened");
        }
        return base;
    }
}
