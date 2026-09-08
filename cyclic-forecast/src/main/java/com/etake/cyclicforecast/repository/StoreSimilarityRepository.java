package com.etake.cyclicforecast.repository;

import com.etake.cyclicforecast.model.BaselineStats;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * self-contained store_similarity implementation (decision #3): among stores that ran the same
 * promo_type for the same product/hierarchy group, preferring the same store_format, picks the one
 * whose own average daily sales is numerically closest to the target store's baseline average daily
 * sales (decision #9). Each candidate store's own best donor row (recency, then higher average daily
 * sales, then promotion_id — decision #4)
 * is resolved via ROW_NUMBER() before ranking stores by distance, so the same tie-break rule applies
 * at both the store level and the donor-row level in a single query.
 */
@Repository
@RequiredArgsConstructor
public class StoreSimilarityRepository {
    private final JdbcClient jdbcClient;

    public BaselineStats computeBaselineStatsForProduct(final String storeId,
                                                          final String productId,
                                                          final LocalDate lookbackFrom,
                                                          final LocalDate asOfDate) {
        return jdbcClient.sql("""
                        SELECT ISNULL(SUM(t.units), 0) AS total_units,
                               COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock
                        FROM transactions t
                                 JOIN inventory i ON i.location_id = t.location_id AND i.product_id = t.product_id AND i.date = t.date
                        WHERE t.location_id = :storeId
                          AND t.product_id = :productId
                          AND t.date BETWEEN :lookbackFrom AND :asOfDate
                          AND i.quantity > 0
                          AND NOT EXISTS (
                              SELECT 1 FROM promotion_history ph2
                              WHERE ph2.store_id = t.location_id AND ph2.product_id = t.product_id
                                AND t.date BETWEEN ph2.start_date AND ph2.end_date)
                        """)
                .param("storeId", storeId)
                .param("productId", productId)
                .param("lookbackFrom", lookbackFrom)
                .param("asOfDate", asOfDate)
                .query(BaselineStats.class)
                .single();
    }

    public BaselineStats computeBaselineStatsForGroup(final String storeId,
                                                        final String thirdSubcategoryId,
                                                        final LocalDate lookbackFrom,
                                                        final LocalDate asOfDate) {
        return jdbcClient.sql("""
                        SELECT ISNULL(SUM(t.units), 0) AS total_units,
                               COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock
                        FROM transactions t
                                 JOIN products p ON p.id = t.product_id
                                 JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                 JOIN inventory i ON i.location_id = t.location_id AND i.product_id = t.product_id AND i.date = t.date
                        WHERE t.location_id = :storeId
                          AND cc.third_subcategory_id = :thirdSubcategoryId
                          AND t.date BETWEEN :lookbackFrom AND :asOfDate
                          AND i.quantity > 0
                          AND NOT EXISTS (
                              SELECT 1 FROM promotion_history ph2
                              WHERE ph2.store_id = t.location_id AND ph2.product_id = t.product_id
                                AND t.date BETWEEN ph2.start_date AND ph2.end_date)
                        """)
                .param("storeId", storeId)
                .param("thirdSubcategoryId", thirdSubcategoryId)
                .param("lookbackFrom", lookbackFrom)
                .param("asOfDate", asOfDate)
                .query(BaselineStats.class)
                .single();
    }

    public Optional<String> findMostSimilarStoreForProduct(final String excludeStoreId,
                                                             final String storeFormat,
                                                             final String productId,
                                                             final String promotionType,
                                                             final LocalDate lookbackFrom,
                                                             final LocalDate beforeDate,
                                                             final BigDecimal targetBaselineAvgDailySales) {
        return jdbcClient.sql("""
                        WITH candidates AS (
                            SELECT ph.store_id AS store_id, ph.promotion_id AS promotion_id, ph.start_date AS start_date,
                                   ISNULL(SUM(t.units), 0) AS total_units,
                                   COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY ph.store_id
                                       ORDER BY ph.start_date DESC,
                                                ISNULL(SUM(t.units), 0) * 1.0 / NULLIF(COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END), 0) DESC,
                                                ph.promotion_id ASC
                                   ) AS rn
                            FROM promotion_history ph
                                     LEFT JOIN transactions t ON t.location_id = ph.store_id AND t.product_id = ph.product_id
                                         AND t.date BETWEEN ph.start_date AND ph.end_date
                                     LEFT JOIN inventory i ON i.location_id = ph.store_id AND i.product_id = ph.product_id
                                         AND i.date BETWEEN ph.start_date AND ph.end_date
                            WHERE ph.product_id = :productId
                              AND ph.promotion_type = :promotionType
                              AND ph.store_id <> :excludeStoreId
                              AND ph.start_date >= :lookbackFrom
                              AND ph.start_date < :beforeDate
                              AND (:storeFormat IS NULL OR ph.store_format = :storeFormat)
                            GROUP BY ph.store_id, ph.promotion_id, ph.start_date
                            HAVING COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) > 0
                        )
                        SELECT TOP (1) store_id
                        FROM candidates
                        WHERE rn = 1
                        ORDER BY ABS(total_units * 1.0 / days_on_stock - :targetBaselineAvgDailySales) ASC,
                                 start_date DESC,
                                 total_units * 1.0 / days_on_stock DESC,
                                 promotion_id ASC
                        """)
                .param("productId", productId)
                .param("promotionType", promotionType)
                .param("excludeStoreId", excludeStoreId)
                .param("lookbackFrom", lookbackFrom)
                .param("beforeDate", beforeDate)
                .param("storeFormat", storeFormat)
                .param("targetBaselineAvgDailySales", targetBaselineAvgDailySales)
                .query(String.class)
                .optional();
    }

    public Optional<String> findMostSimilarStoreForGroup(final String excludeStoreId,
                                                           final String storeFormat,
                                                           final String thirdSubcategoryId,
                                                           final String promotionType,
                                                           final LocalDate lookbackFrom,
                                                           final LocalDate beforeDate,
                                                           final BigDecimal targetBaselineAvgDailySales) {
        return jdbcClient.sql("""
                        WITH candidates AS (
                            SELECT ph.store_id AS store_id, ph.promotion_id AS promotion_id, ph.start_date AS start_date,
                                   ISNULL(SUM(t.units), 0) AS total_units,
                                   COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY ph.store_id
                                       ORDER BY ph.start_date DESC,
                                                ISNULL(SUM(t.units), 0) * 1.0 / NULLIF(COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END), 0) DESC,
                                                ph.promotion_id ASC
                                   ) AS rn
                            FROM promotion_history ph
                                     JOIN products p ON p.id = ph.product_id
                                     JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                     LEFT JOIN transactions t ON t.location_id = ph.store_id AND t.product_id = ph.product_id
                                         AND t.date BETWEEN ph.start_date AND ph.end_date
                                     LEFT JOIN inventory i ON i.location_id = ph.store_id AND i.product_id = ph.product_id
                                         AND i.date BETWEEN ph.start_date AND ph.end_date
                            WHERE cc.third_subcategory_id = :thirdSubcategoryId
                              AND ph.promotion_type = :promotionType
                              AND ph.store_id <> :excludeStoreId
                              AND ph.start_date >= :lookbackFrom
                              AND ph.start_date < :beforeDate
                              AND (:storeFormat IS NULL OR ph.store_format = :storeFormat)
                            GROUP BY ph.store_id, ph.promotion_id, ph.start_date
                            HAVING COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) > 0
                        )
                        SELECT TOP (1) store_id
                        FROM candidates
                        WHERE rn = 1
                        ORDER BY ABS(total_units * 1.0 / days_on_stock - :targetBaselineAvgDailySales) ASC,
                                 start_date DESC,
                                 total_units * 1.0 / days_on_stock DESC,
                                 promotion_id ASC
                        """)
                .param("thirdSubcategoryId", thirdSubcategoryId)
                .param("promotionType", promotionType)
                .param("excludeStoreId", excludeStoreId)
                .param("lookbackFrom", lookbackFrom)
                .param("beforeDate", beforeDate)
                .param("storeFormat", storeFormat)
                .param("targetBaselineAvgDailySales", targetBaselineAvgDailySales)
                .query(String.class)
                .optional();
    }
}
