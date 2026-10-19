package com.sklum.mocks;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Commerce Core's own copy of inventory — intentionally separate from the backend's Postgres
 * (which mirrors a subset for fast reads). SKUs/warehouses match db/init/02-seed.sql so
 * the demo lines up, but this is a different system's data, not a shared database.
 */
@Component
public class InMemoryStore {

    // sku -> warehouseId -> stock
    private final Map<String, Map<String, Integer>> inventory = new ConcurrentHashMap<>();
    private final Map<String, Reservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, Charge> charges = new ConcurrentHashMap<>();
    private final AtomicInteger reservationSeq = new AtomicInteger(1);
    private final AtomicInteger chargeSeq = new AtomicInteger(1);

    public InMemoryStore() {
        seed("SOF-1001", "WH-MAD", 14, "WH-BCN", 6, "WH-PAR", 0);
        seed("SOF-1002", "WH-MAD", 20, "WH-BCN", 11, "WH-PAR", 9);
        seed("SOF-1010", "WH-MAD", 3, "WH-BCN", 0, "WH-PAR", 5);
        seed("SOF-1020", "WH-MAD", 8, "WH-BCN", 8, "WH-PAR", 2);
        seed("CHR-2001", "WH-MAD", 40, "WH-BCN", 35, "WH-PAR", 18);
        seed("CHR-2002", "WH-MAD", 22, "WH-BCN", 14, "WH-PAR", 0);
        seed("CHR-2010", "WH-MAD", 9, "WH-BCN", 0, "WH-PAR", 4);
        seed("CHR-2020", "WH-MAD", 17, "WH-BCN", 12, "WH-PAR", 10);
        seed("TBL-3001", "WH-MAD", 6, "WH-BCN", 2, "WH-PAR", 0);
        seed("TBL-3002", "WH-MAD", 15, "WH-BCN", 9, "WH-PAR", 7);
        seed("TBL-3010", "WH-MAD", 5, "WH-BCN", 5, "WH-PAR", 0);
        seed("TBL-3020", "WH-MAD", 26, "WH-BCN", 19, "WH-PAR", 13);
        seed("LMP-4001", "WH-MAD", 12, "WH-BCN", 8, "WH-PAR", 6);
        seed("LMP-4002", "WH-MAD", 30, "WH-BCN", 21, "WH-PAR", 15);
        seed("LMP-4010", "WH-MAD", 0, "WH-BCN", 7, "WH-PAR", 3);
        seed("LMP-4020", "WH-MAD", 18, "WH-BCN", 10, "WH-PAR", 9);
        seed("DEC-5001", "WH-MAD", 11, "WH-BCN", 6, "WH-PAR", 4);
        seed("DEC-5002", "WH-MAD", 4, "WH-BCN", 0, "WH-PAR", 2);
        seed("DEC-5010", "WH-MAD", 44, "WH-BCN", 38, "WH-PAR", 20);
        seed("DEC-5020", "WH-MAD", 60, "WH-BCN", 50, "WH-PAR", 33);
        seed("OUT-6001", "WH-MAD", 7, "WH-BCN", 5, "WH-PAR", 0);
        seed("OUT-6002", "WH-MAD", 2, "WH-BCN", 0, "WH-PAR", 0);
        seed("OUT-6010", "WH-MAD", 9, "WH-BCN", 6, "WH-PAR", 4);
        seed("OUT-6020", "WH-MAD", 25, "WH-BCN", 17, "WH-PAR", 11);
    }

    private void seed(String sku, String wh1, int s1, String wh2, int s2, String wh3, int s3) {
        Map<String, Integer> byWarehouse = new ConcurrentHashMap<>();
        byWarehouse.put(wh1, s1);
        byWarehouse.put(wh2, s2);
        byWarehouse.put(wh3, s3);
        inventory.put(sku, byWarehouse);
    }

    public Map<String, Integer> stockBySku(String sku) {
        return inventory.getOrDefault(sku, Map.of());
    }

    public synchronized boolean tryReserve(String sku, String warehouseId, int quantity) {
        Map<String, Integer> byWarehouse = inventory.get(sku);
        if (byWarehouse == null) return false;
        Integer current = byWarehouse.get(warehouseId);
        if (current == null || current < quantity) return false;
        byWarehouse.put(warehouseId, current - quantity);
        return true;
    }

    public synchronized void release(String sku, String warehouseId, int quantity) {
        Map<String, Integer> byWarehouse = inventory.get(sku);
        if (byWarehouse == null) return;
        byWarehouse.merge(warehouseId, quantity, Integer::sum);
    }

    public String nextReservationId() {
        return "RSV-" + reservationSeq.getAndIncrement();
    }

    public String nextChargeId() {
        return "CHG-" + chargeSeq.getAndIncrement();
    }

    public void putReservation(String id, Reservation r) {
        reservations.put(id, r);
    }

    public Reservation getReservation(String id) {
        return reservations.get(id);
    }

    public void putCharge(String id, Charge c) {
        charges.put(id, c);
    }

    public Charge getCharge(String id) {
        return charges.get(id);
    }

    public record Reservation(String sku, String warehouseId, int quantity) {}
    public record Charge(String orderId, long amountCents) {}
}
