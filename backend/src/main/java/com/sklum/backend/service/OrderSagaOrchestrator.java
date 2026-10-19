package com.sklum.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sklum.backend.client.CommerceCoreClient;
import com.sklum.backend.client.PaymentClient;
import com.sklum.backend.model.CheckoutRequest;
import com.sklum.backend.model.CheckoutResponse;
import com.sklum.backend.model.PromiseWindow;
import com.sklum.backend.repository.IdempotencyRepository;
import com.sklum.backend.repository.OrderRepository;
import com.sklum.backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Fig. 5's saga, as code: Reserve -> Charge -> Confirm, with an explicit compensating action
 * on every failure path. Nothing here is "transactional" in the database sense — each step is
 * a separate call to a separate system, and the whole point of writing it out longhand (rather
 * than wrapping it in a {@code @Transactional}) is that a cross-system saga can't be. The
 * {@code simulateFailure} field lets a reviewer force any one step to fail and watch the
 * compensations run.
 */
@Service
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    private final ProductRepository productRepository;
    private final DeliveryPromiseService deliveryPromiseService;
    private final CommerceCoreClient commerceCoreClient;
    private final PaymentClient paymentClient;
    private final OrderRepository orderRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public OrderSagaOrchestrator(ProductRepository productRepository,
                                  DeliveryPromiseService deliveryPromiseService,
                                  CommerceCoreClient commerceCoreClient,
                                  PaymentClient paymentClient,
                                  OrderRepository orderRepository,
                                  IdempotencyRepository idempotencyRepository,
                                  ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.deliveryPromiseService = deliveryPromiseService;
        this.commerceCoreClient = commerceCoreClient;
        this.paymentClient = paymentClient;
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotency-Key handling (optional — omit the header to get the old, un-deduplicated
     * behaviour): a new key claims a row and runs the saga once; a key that already finished
     * replays its stored response instead of reserving/charging/confirming a second time; a key
     * whose first request is still mid-flight is rejected with 409 rather than silently racing
     * it — this prototype doesn't attempt to block-and-wait for the first one to finish.
     */
    public CheckoutResponse checkout(CheckoutRequest request, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return runSaga(request, UUID.randomUUID());
        }

        UUID orderId = UUID.randomUUID();
        if (!idempotencyRepository.tryClaim(idempotencyKey, orderId)) {
            var existing = idempotencyRepository.find(idempotencyKey)
                    .orElseThrow(() -> new IdempotencyConflictException(
                            "Idempotency-Key is being processed, try again shortly"));
            if ("COMPLETED".equals(existing.status())) {
                return readValue(existing.responseBodyJson());
            }
            throw new IdempotencyConflictException(
                    "A request with this Idempotency-Key is still being processed");
        }

        CheckoutResponse response = runSaga(request, orderId);
        idempotencyRepository.complete(idempotencyKey, writeValue(response));
        return response;
    }

    private CheckoutResponse readValue(String json) {
        try {
            return objectMapper.readValue(json, CheckoutResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("corrupt stored idempotent response", e);
        }
    }

    private String writeValue(CheckoutResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new IllegalStateException("could not serialize checkout response", e);
        }
    }

    private CheckoutResponse runSaga(CheckoutRequest request, UUID orderId) {
        List<String> stepLog = new ArrayList<>();

        var product = productRepository.findBySku(request.sku())
                .orElseThrow(() -> new NoSuchElementException("no such product: " + request.sku()));

        // refine promise (same-row connector in Fig. 4: saga -> DeliveryPromiseService)
        PromiseWindow promise = deliveryPromiseService.checkoutPromise(request.sku(), request.postalCode());
        String warehouseId = promise.warehouseId();
        if (warehouseId == null) {
            orderRepository.insertOrder(orderId, request.sku(), request.quantity(), null, request.postalCode(),
                    "FAILED", null, null);
            orderRepository.insertSagaStep(orderId, "RESERVE", "FAILED", "no warehouse with a known promise for this SKU/postcode");
            return new CheckoutResponse(orderId.toString(), "FAILED", promise, List.of("RESERVE:FAILED"),
                    "No delivery option available for this product/postcode.");
        }

        orderRepository.insertOrder(orderId, request.sku(), request.quantity(), warehouseId, request.postalCode(),
                "PENDING", promise.minDays(), promise.maxDays());

        boolean failReserve = "reserve".equalsIgnoreCase(request.simulateFailure());
        boolean failPayment = "payment".equalsIgnoreCase(request.simulateFailure());
        boolean failConfirm = "confirm".equalsIgnoreCase(request.simulateFailure());

        // --- Step 1: Reserve ---
        var reserveResult = commerceCoreClient.reserve(orderId.toString(), request.sku(), warehouseId,
                request.quantity(), failReserve);
        if (!reserveResult.success()) {
            orderRepository.insertSagaStep(orderId, "RESERVE", "FAILED", reserveResult.error());
            orderRepository.updateStatus(orderId, "FAILED");
            stepLog.add("RESERVE:FAILED");
            return new CheckoutResponse(orderId.toString(), "FAILED", promise, stepLog,
                    "Could not reserve stock: " + reserveResult.error());
        }
        orderRepository.insertSagaStep(orderId, "RESERVE", "SUCCESS", reserveResult.reservationId());
        stepLog.add("RESERVE:SUCCESS");

        // --- Step 2: Charge ---
        long amountCents = (long) product.priceCents() * request.quantity();
        var chargeResult = paymentClient.charge(orderId.toString(), amountCents, failPayment);
        if (!chargeResult.success()) {
            orderRepository.insertSagaStep(orderId, "CHARGE", "FAILED", chargeResult.error());
            stepLog.add("CHARGE:FAILED");

            commerceCoreClient.release(reserveResult.reservationId());
            orderRepository.insertSagaStep(orderId, "COMPENSATE_RESERVE", "SUCCESS", reserveResult.reservationId());
            stepLog.add("COMPENSATE_RESERVE:SUCCESS");

            orderRepository.updateStatus(orderId, "COMPENSATED");
            return new CheckoutResponse(orderId.toString(), "COMPENSATED", promise, stepLog,
                    "Payment failed, reservation released: " + chargeResult.error());
        }
        orderRepository.insertSagaStep(orderId, "CHARGE", "SUCCESS", chargeResult.chargeId());
        stepLog.add("CHARGE:SUCCESS");

        // --- Step 3: Confirm ---
        var confirmResult = commerceCoreClient.confirm(orderId.toString(), failConfirm);
        if (!confirmResult.success()) {
            orderRepository.insertSagaStep(orderId, "CONFIRM", "FAILED", confirmResult.error());
            stepLog.add("CONFIRM:FAILED");

            paymentClient.refund(chargeResult.chargeId());
            orderRepository.insertSagaStep(orderId, "COMPENSATE_CHARGE", "SUCCESS", chargeResult.chargeId());
            stepLog.add("COMPENSATE_CHARGE:SUCCESS");

            commerceCoreClient.release(reserveResult.reservationId());
            orderRepository.insertSagaStep(orderId, "COMPENSATE_RESERVE", "SUCCESS", reserveResult.reservationId());
            stepLog.add("COMPENSATE_RESERVE:SUCCESS");

            orderRepository.updateStatus(orderId, "COMPENSATED");
            return new CheckoutResponse(orderId.toString(), "COMPENSATED", promise, stepLog,
                    "Order confirmation failed, payment refunded and reservation released: " + confirmResult.error());
        }
        orderRepository.insertSagaStep(orderId, "CONFIRM", "SUCCESS", null);
        stepLog.add("CONFIRM:SUCCESS");

        orderRepository.updateStatus(orderId, "CONFIRMED");
        log.info("order {} confirmed: sku={} warehouse={} promise={}-{}d", orderId, request.sku(), warehouseId,
                promise.minDays(), promise.maxDays());
        return new CheckoutResponse(orderId.toString(), "CONFIRMED", promise, stepLog, "Order confirmed.");
    }
}
