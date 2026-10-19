package com.sklum.backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Backs the Idempotency-Key header on POST /checkout — see OrderSagaOrchestrator. */
@Repository
public class IdempotencyRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public IdempotencyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Atomically claims the key for this orderId. Returns true if this call is the first to
     *  see the key (the caller should run the saga); false if someone already claimed it. */
    public boolean tryClaim(String key, UUID orderId) {
        String sql = """
                INSERT INTO idempotency_keys (idempotency_key, order_id, status, created_at)
                VALUES (:key, :orderId, 'IN_PROGRESS', now())
                ON CONFLICT (idempotency_key) DO NOTHING
                """;
        int rows = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("key", key).addValue("orderId", orderId));
        return rows == 1;
    }

    public void complete(String key, String responseBodyJson) {
        jdbc.update("UPDATE idempotency_keys SET status = 'COMPLETED', response_body = :body WHERE idempotency_key = :key",
                new MapSqlParameterSource().addValue("body", responseBodyJson).addValue("key", key));
    }

    public Optional<StoredResult> find(String key) {
        String sql = "SELECT status, response_body FROM idempotency_keys WHERE idempotency_key = :key";
        return jdbc.query(sql, new MapSqlParameterSource("key", key), rs ->
                rs.next() ? Optional.of(new StoredResult(rs.getString("status"), rs.getString("response_body")))
                        : Optional.empty());
    }

    public record StoredResult(String status, String responseBodyJson) {}
}
