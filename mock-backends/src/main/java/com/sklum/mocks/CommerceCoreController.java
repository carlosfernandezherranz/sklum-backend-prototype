package com.sklum.mocks;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/commerce-core")
public class CommerceCoreController {

    private final InMemoryStore store;

    public CommerceCoreController(InMemoryStore store) {
        this.store = store;
    }

    @GetMapping("/inventory/{sku}")
    public ResponseEntity<List<Map<String, Object>>> inventory(@PathVariable String sku) {
        simulateLatency(10, 60);
        List<Map<String, Object>> rows = store.stockBySku(sku).entrySet().stream()
                .map(e -> Map.<String, Object>of("warehouseId", e.getKey(), "stockLevel", e.getValue()))
                .toList();
        return ResponseEntity.ok(rows);
    }

    public record ReserveRequest(String orderId, String sku, String warehouseId, int quantity) {}

    @PostMapping("/reservations")
    public ResponseEntity<?> reserve(@RequestBody ReserveRequest req,
                                      @RequestParam(defaultValue = "false") boolean simulateFailure) {
        simulateLatency(20, 120);
        if (simulateFailure) {
            return ResponseEntity.status(500).body(Map.of("error", "commerce-core reserve: simulated failure"));
        }
        boolean ok = store.tryReserve(req.sku(), req.warehouseId(), req.quantity());
        if (!ok) {
            return ResponseEntity.status(409).body(Map.of("error", "insufficient stock at " + req.warehouseId()));
        }
        String reservationId = store.nextReservationId();
        store.putReservation(reservationId, new InMemoryStore.Reservation(req.sku(), req.warehouseId(), req.quantity()));
        return ResponseEntity.ok(Map.of("reservationId", reservationId));
    }

    @PostMapping("/reservations/{reservationId}/release")
    public ResponseEntity<?> release(@PathVariable String reservationId) {
        simulateLatency(10, 50);
        InMemoryStore.Reservation r = store.getReservation(reservationId);
        if (r == null) {
            return ResponseEntity.status(404).body(Map.of("error", "no such reservation"));
        }
        store.release(r.sku(), r.warehouseId(), r.quantity());
        return ResponseEntity.ok(Map.of("released", true));
    }

    @PostMapping("/orders/{orderId}/confirm")
    public ResponseEntity<?> confirm(@PathVariable String orderId,
                                      @RequestParam(defaultValue = "false") boolean simulateFailure) {
        simulateLatency(20, 90);
        if (simulateFailure) {
            return ResponseEntity.status(500).body(Map.of("error", "commerce-core confirm: simulated failure"));
        }
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", "CONFIRMED"));
    }

    static void simulateLatency(int minMs, int maxMs) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
