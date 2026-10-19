package com.sklum.backend.repository;

import com.sklum.backend.model.ProductDetail;
import com.sklum.backend.model.ProductSummary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Plain NamedParameterJdbcTemplate, no ORM — per CLAUDE.md's "DB access layer" decision. The
 * trigram search is deliberately explicit SQL: pg_trgm's {@code %} operator and
 * {@code similarity()} are what replace Elasticsearch for this catalog size, and that's worth
 * seeing directly rather than hiding behind a repository abstraction's query derivation.
 */
@Repository
public class ProductRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ProductRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProductSummary> search(String query, int limit) {
        // word_similarity()/<% , not similarity()/% : the latter scores the *whole* strings
        // against each other, so a short query word against a long product name scores low
        // almost by construction. word_similarity() scores the query against its best-matching
        // substring instead — the right comparison for "does this word appear, even typo'd".
        // Threshold is set once at the database level (see db/init/01-schema.sql) so the <%
        // operator stays index-accelerated (GIN, gin_trgm_ops) rather than filtering in SQL here.
        String sql = """
                SELECT sku, name, category, price_cents,
                       GREATEST(word_similarity(:query, name), word_similarity(:query, description),
                                 word_similarity(:query, category)) AS score
                FROM products
                WHERE :query <% name OR :query <% description OR :query <% category
                ORDER BY score DESC, name ASC
                LIMIT :limit
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("query", query)
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, rowNum) -> new ProductSummary(
                rs.getString("sku"), rs.getString("name"), rs.getString("category"), rs.getInt("price_cents")));
    }

    public Optional<ProductDetail> findBySku(String sku) {
        String sql = "SELECT sku, name, category, description, price_cents, currency FROM products WHERE sku = :sku";
        try {
            ProductDetail detail = jdbc.queryForObject(sql, new MapSqlParameterSource("sku", sku), (rs, rowNum) ->
                    new ProductDetail(rs.getString("sku"), rs.getString("name"), rs.getString("category"),
                            rs.getString("description"), rs.getInt("price_cents"), rs.getString("currency")));
            return Optional.ofNullable(detail);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<String> allSkus() {
        return jdbc.getJdbcTemplate().queryForList("SELECT sku FROM products", String.class);
    }
}
