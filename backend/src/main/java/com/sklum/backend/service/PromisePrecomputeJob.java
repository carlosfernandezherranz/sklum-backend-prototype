package com.sklum.backend.service;

import com.sklum.backend.client.CarrierClient;
import com.sklum.backend.client.CarrierEta;
import com.sklum.backend.client.CommerceCoreClient;
import com.sklum.backend.repository.ProductRepository;
import com.sklum.backend.repository.PromiseLookupRepository;
import com.sklum.backend.repository.PromiseRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Fig. 4's scheduled component: reads carrier ETAs + Commerce Core inventory, writes
 * promise_lookup. Runs once at startup (so the table isn't empty for a demo) and then on a
 * fixed interval, standing in for "scheduled + triggered on low-stock" from the system design
 * — a real implementation would also trigger on inventory-change events, which this prototype
 * simplifies to a timer.
 *
 * A small fixed set of postal zones stands in for full EU postcode coverage — enough to
 * demonstrate the coarse/fine two-tier mechanism without precomputing all ~4,500 SKUs x every
 * postcode, which is exactly the kind of scope control the business case already calls for.
 */
@Component
public class PromisePrecomputeJob {

    private static final Logger log = LoggerFactory.getLogger(PromisePrecomputeJob.class);

    private static final Map<String, List<String>> ZONE_SAMPLE_POSTCODES = Map.of(
            "28", List.of("28013", "28045"), // Madrid
            "08", List.of("08014", "08029"), // Barcelona
            "75", List.of("75011", "75016")  // Paris
    );

    private final ProductRepository productRepository;
    private final CommerceCoreClient commerceCoreClient;
    private final CarrierClient carrierClient;
    private final PromiseLookupRepository promiseLookupRepository;

    public PromisePrecomputeJob(ProductRepository productRepository, CommerceCoreClient commerceCoreClient,
                                 CarrierClient carrierClient, PromiseLookupRepository promiseLookupRepository) {
        this.productRepository = productRepository;
        this.commerceCoreClient = commerceCoreClient;
        this.carrierClient = carrierClient;
        this.promiseLookupRepository = promiseLookupRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        run();
    }

    @Scheduled(initialDelayString = "${promise.precompute.interval-ms:600000}",
               fixedDelayString = "${promise.precompute.interval-ms:600000}")
    public void scheduledRun() {
        run();
    }

    public void run() {
        log.info("PromisePrecomputeJob: starting run");
        int written = 0;
        for (String sku : productRepository.allSkus()) {
            var stock = commerceCoreClient.inventory(sku);
            for (var inv : stock) {
                if (inv.stockLevel() <= 0) continue; // no point promising a date for stock that isn't there
                for (String zone : ZONE_SAMPLE_POSTCODES.keySet()) {
                    CarrierEta best = pickFastest(inv.warehouseId(), zone);
                    if (best == null) continue; // both carriers degraded — leave any prior row in place

                    promiseLookupRepository.upsert(new PromiseRow(sku, inv.warehouseId(), "ZONE", zone,
                            best.carrierId(), best.minDays(), best.maxDays()));
                    written++;

                    for (String postcode : ZONE_SAMPLE_POSTCODES.get(zone)) {
                        promiseLookupRepository.upsert(new PromiseRow(sku, inv.warehouseId(), "POSTCODE", postcode,
                                best.carrierId(), best.minDays(), best.maxDays()));
                        written++;
                    }
                }
            }
        }
        log.info("PromisePrecomputeJob: wrote/updated {} rows", written);
    }

    private CarrierEta pickFastest(String warehouseId, String zone) {
        CarrierEta a = carrierClient.etaCarrierA(warehouseId, zone);
        CarrierEta b = carrierClient.etaCarrierB(warehouseId, zone);
        if (!a.available() && !b.available()) return null;
        if (!a.available()) return b;
        if (!b.available()) return a;
        return a.maxDays() <= b.maxDays() ? a : b;
    }
}
