package com.sklum.backend.repository;

import com.sklum.backend.model.DeliverySummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Orders + the saga's own audit log (Fig. 4's OrderRepository) — what makes the "strong
 * consistency" claim on the order write path verifiable, not just asserted. */
@Repository
public class OrderRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public OrderRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertOrder(UUID orderId, String sku, int quantity, String warehouseId, String postalCode,
                             String status, Integer promiseMinDays, Integer promiseMaxDays) {
        String sql = """
                INSERT INTO orders (order_id, sku, quantity, warehouse_id, postal_code, status,
                                     promise_min_days, promise_max_days, created_at)
                VALUES (:orderId, :sku, :quantity, :warehouseId, :postalCode, :status,
                        :promiseMinDays, :promiseMaxDays, now())
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("sku", sku)
                .addValue("quantity", quantity)
                .addValue("warehouseId", warehouseId)
                .addValue("postalCode", postalCode)
                .addValue("status", status)
                .addValue("promiseMinDays", promiseMinDays)
                .addValue("promiseMaxDays", promiseMaxDays));
    }

    public void updateStatus(UUID orderId, String status) {
        jdbc.update("UPDATE orders SET status = :status WHERE order_id = :orderId",
                new MapSqlParameterSource().addValue("status", status).addValue("orderId", orderId));
    }

    public void insertSagaStep(UUID orderId, String stepName, String status, String detail) {
        String sql = """
                INSERT INTO saga_steps (order_id, step_name, status, detail, occurred_at)
                VALUES (:orderId, :stepName, :status, :detail, now())
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("stepName", stepName)
                .addValue("status", status).addValue("detail", detail));
    }

    public List<DeliverySummary> recentOrders(int limit) {
        String sql = """
                SELECT order_id, sku, status, promise_min_days, promise_max_days, created_at
                FROM orders ORDER BY created_at DESC LIMIT :limit
                """;
        return jdbc.query(sql, new MapSqlParameterSource("limit", limit), (rs, rowNum) -> new DeliverySummary(
                rs.getString("order_id"), rs.getString("sku"), rs.getString("status"),
                (Integer) rs.getObject("promise_min_days"), (Integer) rs.getObject("promise_max_days"),
                rs.getTimestamp("created_at").toLocalDateTime()));
    }

    public Map<String, Integer> orderStatusCounts() {
        String sql = "SELECT status, count(*) AS c FROM orders GROUP BY status";
        return jdbc.query(sql, rs -> {
            Map<String, Integer> out = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                out.put(rs.getString("status"), rs.getInt("c"));
            }
            return out;
        });
    }

    public Map<String, Integer> sagaStepCounts() {
        String sql = "SELECT step_name, count(*) AS c FROM saga_steps GROUP BY step_name ORDER BY step_name";
        return jdbc.query(sql, rs -> {
            Map<String, Integer> out = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                out.put(rs.getString("step_name"), rs.getInt("c"));
            }
            return out;
        });
    }
}
