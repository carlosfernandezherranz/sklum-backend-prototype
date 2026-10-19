-- Supports an optional Idempotency-Key header on POST /checkout: a client retrying a timed-out
-- or unacknowledged request should get back the ORIGINAL saga's result, not run
-- Reserve/Charge/Confirm a second time. See OrderSagaOrchestrator.checkout(request, key) and
-- CLAUDE.md's "Idempotency key" decision.
-- No FK to orders(order_id): the key is claimed BEFORE the order row is inserted (that's the
-- whole point — claim first, then run the saga, so a concurrent duplicate sees the claim), so
-- a FK here would always fail on the very first insert.
CREATE TABLE idempotency_keys (
  idempotency_key   VARCHAR(100) PRIMARY KEY,
  order_id          UUID NOT NULL,
  status            VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
  response_body     TEXT,
  created_at        TIMESTAMP NOT NULL DEFAULT now()
);
