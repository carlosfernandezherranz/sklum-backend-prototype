-- Two gaps found on review (see CLAUDE.md): neither is a primary key, so Postgres doesn't
-- auto-index them.

-- OrderRepository.recentOrders(): ORDER BY created_at DESC LIMIT :limit — a full scan + sort
-- without this at any real order volume.
CREATE INDEX idx_orders_created_at ON orders (created_at DESC);

-- saga_steps.order_id is a foreign key but FKs aren't auto-indexed in Postgres (unlike PKs).
-- No current query filters by it, but it's the obvious lookup for a per-order saga history
-- (e.g. "GET /orders/{id}/saga") — the natural next read this schema doesn't support yet.
CREATE INDEX idx_saga_steps_order_id ON saga_steps (order_id);
