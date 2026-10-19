-- SKLUM BFF prototype — serving store schema
-- Mirrors Fig. 2/Fig. 4 of the system design: this is the BFF's OWN Postgres,
-- separate from the mock Commerce Core's data (mock-backends has its own in-memory copy).

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- pg_trgm's word_similarity default threshold (0.6) is tuned for matching whole short strings,
-- not a single typo'd word against one word *within* a longer product name — 0.3 catches
-- realistic typos ("sofaa", "tabel") without materially increasing false positives at this
-- catalog's vocabulary. Set at the database level so every connection (including the BFF's
-- pool) picks it up without relying on session state.
ALTER DATABASE sklum SET pg_trgm.word_similarity_threshold = 0.3;

CREATE TABLE products (
  sku           VARCHAR(20) PRIMARY KEY,
  name          VARCHAR(200) NOT NULL,
  category      VARCHAR(80) NOT NULL,
  description   TEXT NOT NULL,
  price_cents   INTEGER NOT NULL,
  currency      CHAR(3) NOT NULL DEFAULT 'EUR'
);

-- trigram GIN indexes power the fuzzy/typo-tolerant search (replaces Elasticsearch, per the
-- business case: deliberately simplified given the ~4,500 SKU catalog size).
CREATE INDEX idx_products_name_trgm ON products USING gin (name gin_trgm_ops);
CREATE INDEX idx_products_desc_trgm ON products USING gin (description gin_trgm_ops);
CREATE INDEX idx_products_category_trgm ON products USING gin (category gin_trgm_ops);

CREATE TABLE warehouses (
  warehouse_id  VARCHAR(10) PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  country       CHAR(2) NOT NULL
);

CREATE TABLE inventory (
  sku           VARCHAR(20) NOT NULL REFERENCES products(sku),
  warehouse_id  VARCHAR(10) NOT NULL REFERENCES warehouses(warehouse_id),
  stock_level   INTEGER NOT NULL,
  PRIMARY KEY (sku, warehouse_id)
);

-- Written by PromisePrecomputeJob (Fig. 4). granularity='ZONE' rows back the PDP's coarse promise;
-- granularity='POSTCODE' rows back checkout's finer-grained read. See Fig. 3/Fig. 5 for the mechanism.
CREATE TABLE promise_lookup (
  sku           VARCHAR(20) NOT NULL,
  warehouse_id  VARCHAR(10) NOT NULL,
  granularity   VARCHAR(10) NOT NULL CHECK (granularity IN ('ZONE', 'POSTCODE')),
  zone_key      VARCHAR(10) NOT NULL,
  carrier_id    VARCHAR(20) NOT NULL,
  min_days      INTEGER NOT NULL,
  max_days      INTEGER NOT NULL,
  computed_at   TIMESTAMP NOT NULL DEFAULT now(),
  PRIMARY KEY (sku, warehouse_id, granularity, zone_key)
);
-- Needed because the read path (DeliveryPromiseService) searches by (sku, granularity, zone_key)
-- across *all* warehouses, which skips the PK's leading warehouse_id column — the PK index alone
-- can't serve that query efficiently.
CREATE INDEX idx_promise_lookup_sku_zone ON promise_lookup (sku, granularity, zone_key);

CREATE TABLE orders (
  order_id          UUID PRIMARY KEY,
  sku               VARCHAR(20) NOT NULL,
  quantity          INTEGER NOT NULL,
  warehouse_id      VARCHAR(10),
  postal_code       VARCHAR(10) NOT NULL,
  status            VARCHAR(20) NOT NULL,
  promise_min_days  INTEGER,
  promise_max_days  INTEGER,
  created_at        TIMESTAMP NOT NULL DEFAULT now()
);

-- Audit log of every saga step, success or failure — this is what Fig. 5's compensation
-- arrows actually produce, and what the ops dashboard reads for saga.compensations.
CREATE TABLE saga_steps (
  id          BIGSERIAL PRIMARY KEY,
  order_id    UUID NOT NULL REFERENCES orders(order_id),
  step_name   VARCHAR(30) NOT NULL,
  status      VARCHAR(20) NOT NULL,
  detail      TEXT,
  occurred_at TIMESTAMP NOT NULL DEFAULT now()
);
