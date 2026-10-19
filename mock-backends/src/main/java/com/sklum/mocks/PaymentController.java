package com.sklum.mocks;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payment")
public class PaymentController {

    private final InMemoryStore store;

    public PaymentController(InMemoryStore store) {
        this.store = store;
    }

    public record ChargeRequest(String orderId, long amountCents) {}

    @PostMapping("/charges")
    public ResponseEntity<?> charge(@RequestBody ChargeRequest req,
                                     @RequestParam(defaultValue = "false") boolean simulateFailure) {
        CommerceCoreController.simulateLatency(30, 150);
        if (simulateFailure) {
            return ResponseEntity.status(402).body(Map.of("error", "payment: simulated decline"));
        }
        String chargeId = store.nextChargeId();
        store.putCharge(chargeId, new InMemoryStore.Charge(req.orderId(), req.amountCents()));
        return ResponseEntity.ok(Map.of("chargeId", chargeId));
    }

    @PostMapping("/charges/{chargeId}/refund")
    public ResponseEntity<?> refund(@PathVariable String chargeId) {
        CommerceCoreController.simulateLatency(20, 100);
        InMemoryStore.Charge c = store.getCharge(chargeId);
        if (c == null) {
            return ResponseEntity.status(404).body(Map.of("error", "no such charge"));
        }
        return ResponseEntity.ok(Map.of("refunded", true, "chargeId", chargeId));
    }
}
