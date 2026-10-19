package com.sklum.backend.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Read side backs Fig. 3/Fig. 5's two-tier delivery promise: ZONE rows for the PDP (coarse,
 * cheap), POSTCODE rows for checkout (fine-grained). Write side is PromisePrecomputeJob —
 * nothing else writes here, which is exactly the producer/consumer split Fig. 4 draws out.
 */
@Repository
public class PromiseLookupRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public PromiseLookupRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Best (fastest) ZONE-granularity promise for this SKU across warehouses that have stock. */
    public Optional<PromiseRow> findBestZone(String sku, String zoneKey) {
        String sql = """
                SELECT sku, warehouse_id, granularity, zone_key, carrier_id, min_days, max_days
                FROM promise_lookup
                WHERE sku = :sku AND granularity = 'ZONE' AND zone_key = :zoneKey
                ORDER BY max_days ASC, min_days ASC
                LIMIT 1
                """;
        return queryOne(sql, new MapSqlParameterSource().addValue("sku", sku).addValue("zoneKey", zoneKey));
    }

    /** Best POSTCODE-granularity promise for this exact postcode, if the precompute job has one. */
    public Optional<PromiseRow> findBestPostcode(String sku, String postcodeKey) {
        String sql = """
                SELECT sku, warehouse_id, granularity, zone_key, carrier_id, min_days, max_days
                FROM promise_lookup
                WHERE sku = :sku AND granularity = 'POSTCODE' AND zone_key = :zoneKey
                ORDER BY max_days ASC, min_days ASC
                LIMIT 1
                """;
        return queryOne(sql, new MapSqlParameterSource().addValue("sku", sku).addValue("zoneKey", postcodeKey));
    }

    private Optional<PromiseRow> queryOne(String sql, MapSqlParameterSource params) {
        try {
            PromiseRow row = jdbc.queryForObject(sql, params, (rs, rowNum) -> new PromiseRow(
                    rs.getString("sku"), rs.getString("warehouse_id"), rs.getString("granularity"),
                    rs.getString("zone_key"), rs.getString("carrier_id"), rs.getInt("min_days"), rs.getInt("max_days")));
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public void upsert(PromiseRow row) {
        String sql = """
                INSERT INTO promise_lookup (sku, warehouse_id, granularity, zone_key, carrier_id, min_days, max_days, computed_at)
                VALUES (:sku, :warehouseId, :granularity, :zoneKey, :carrierId, :minDays, :maxDays, now())
                ON CONFLICT (sku, warehouse_id, granularity, zone_key)
                DO UPDATE SET carrier_id = EXCLUDED.carrier_id, min_days = EXCLUDED.min_days,
                              max_days = EXCLUDED.max_days, computed_at = now()
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("sku", row.sku())
                .addValue("warehouseId", row.warehouseId())
                .addValue("granularity", row.granularity())
                .addValue("zoneKey", row.zoneKey())
                .addValue("carrierId", row.carrierId())
                .addValue("minDays", row.minDays())
                .addValue("maxDays", row.maxDays()));
    }

    public int countRows() {
        Integer count = jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM promise_lookup", Integer.class);
        return count == null ? 0 : count;
    }
}
