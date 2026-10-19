package com.sklum.mocks;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Two simulated carriers under one controller (route groups /carrier/a, /carrier/b — see
 * CLAUDE.md "Mock topology"). ETAs are deterministic per (warehouse, zone) so the precompute
 * job's "pick the fastest" logic produces stable, demonstrable results across runs.
 */
@RestController
@RequestMapping("/carrier")
public class CarrierController {

    @GetMapping("/a/eta")
    public ResponseEntity<?> carrierA(@RequestParam String warehouseId, @RequestParam String zone) {
        CommerceCoreController.simulateLatency(15, 70);
        int bucket = Math.floorMod((warehouseId + zone).hashCode(), 3);
        int minDays = 1 + bucket;
        int maxDays = minDays + 2;
        return ResponseEntity.ok(Map.of(
                "carrierId", "CARRIER-A", "warehouseId", warehouseId, "zone", zone,
                "minDays", minDays, "maxDays", maxDays, "capacityOk", true));
    }

    @GetMapping("/b/eta")
    public ResponseEntity<?> carrierB(@RequestParam String warehouseId, @RequestParam String zone) {
        CommerceCoreController.simulateLatency(15, 70);
        int bucket = Math.floorMod((warehouseId + zone).hashCode(), 2);
        int minDays = 2 + bucket;
        int maxDays = minDays + 1;
        return ResponseEntity.ok(Map.of(
                "carrierId", "CARRIER-B", "warehouseId", warehouseId, "zone", zone,
                "minDays", minDays, "maxDays", maxDays, "capacityOk", true));
    }
}
