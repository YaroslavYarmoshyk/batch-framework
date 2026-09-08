package com.etake.cyclicforecast.repository;

import com.etake.cyclicforecast.model.DonorCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolves the single winning donor row for a cascade level: candidate selection, the decision #2
 * zero-stock disqualification and the decision #4 tie-break (recency, then higher average daily sales, then lowest
 * promotion_id) all happen in one query via HAVING + ORDER BY + TOP (1).
 */
@Repository
@RequiredArgsConstructor
public class DonorCandidateRepository {
    private final JdbcClient jdbcClient;

    public Optional<DonorCandidate> findProductDonor(final String storeId,
                                                       final String productId,
                                                       final String promotionType,
                                                       final LocalDate lookbackFrom,
                                                       final LocalDate beforeDate,
                                                       final String excludeId) {
        return jdbcClient.sql("""
                        SELECT TOP (1) ph.promotion_id AS promotion_id,
                                       ph.store_id      AS store_id,
                                       ph.product_id    AS product_id,
                                       ph.start_date    AS start_date,
                                       ISNULL(SUM(t.units), 0) AS total_units,
                                       COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock
                        FROM promotion_history ph
                                 LEFT JOIN transactions t ON t.location_id = ph.store_id AND t.product_id = ph.product_id
                                     AND t.date BETWEEN ph.start_date AND ph.end_date
                                 LEFT JOIN inventory i ON i.location_id = ph.store_id AND i.product_id = ph.product_id
                                     AND i.date BETWEEN ph.start_date AND ph.end_date
                        WHERE ph.store_id = :storeId
                          AND ph.product_id = :productId
                          AND ph.promotion_type = :promotionType
                          AND ph.start_date >= :lookbackFrom
                          AND ph.start_date < :beforeDate
                          AND ph.id <> :excludeId
                        GROUP BY ph.promotion_id, ph.store_id, ph.product_id, ph.start_date
                        HAVING COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) > 0
                        ORDER BY ph.start_date DESC,
                                 ISNULL(SUM(t.units), 0) * 1.0 / COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) DESC,
                                 ph.promotion_id ASC
                        """)
                .param("storeId", storeId)
                .param("productId", productId)
                .param("promotionType", promotionType)
                .param("lookbackFrom", lookbackFrom)
                .param("beforeDate", beforeDate)
                .param("excludeId", excludeId)
                .query(DonorCandidate.class)
                .optional();
    }

    public Optional<DonorCandidate> findThirdSubcategoryDonor(final String storeId,
                                                                final String thirdSubcategoryId,
                                                                final String promotionType,
                                                                final LocalDate lookbackFrom,
                                                                final LocalDate beforeDate,
                                                                final String excludeId) {
        return jdbcClient.sql("""
                        SELECT TOP (1) ph.promotion_id AS promotion_id,
                                       ph.store_id      AS store_id,
                                       ph.product_id    AS product_id,
                                       ph.start_date    AS start_date,
                                       ISNULL(SUM(t.units), 0) AS total_units,
                                       COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock
                        FROM promotion_history ph
                                 JOIN products p ON p.id = ph.product_id
                                 JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                 LEFT JOIN transactions t ON t.location_id = ph.store_id AND t.product_id = ph.product_id
                                     AND t.date BETWEEN ph.start_date AND ph.end_date
                                 LEFT JOIN inventory i ON i.location_id = ph.store_id AND i.product_id = ph.product_id
                                     AND i.date BETWEEN ph.start_date AND ph.end_date
                        WHERE ph.store_id = :storeId
                          AND cc.third_subcategory_id = :thirdSubcategoryId
                          AND ph.promotion_type = :promotionType
                          AND ph.start_date >= :lookbackFrom
                          AND ph.start_date < :beforeDate
                          AND ph.id <> :excludeId
                        GROUP BY ph.promotion_id, ph.store_id, ph.product_id, ph.start_date
                        HAVING COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) > 0
                        ORDER BY ph.start_date DESC,
                                 ISNULL(SUM(t.units), 0) * 1.0 / COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) DESC,
                                 ph.promotion_id ASC
                        """)
                .param("storeId", storeId)
                .param("thirdSubcategoryId", thirdSubcategoryId)
                .param("promotionType", promotionType)
                .param("lookbackFrom", lookbackFrom)
                .param("beforeDate", beforeDate)
                .param("excludeId", excludeId)
                .query(DonorCandidate.class)
                .optional();
    }

    public Optional<DonorCandidate> findSecondSubcategoryDonor(final String storeId,
                                                                 final String secondSubcategoryId,
                                                                 final String promotionType,
                                                                 final LocalDate lookbackFrom,
                                                                 final LocalDate beforeDate,
                                                                 final String excludeId) {
        return jdbcClient.sql("""
                        SELECT TOP (1) ph.promotion_id AS promotion_id,
                                       ph.store_id      AS store_id,
                                       ph.product_id    AS product_id,
                                       ph.start_date    AS start_date,
                                       ISNULL(SUM(t.units), 0) AS total_units,
                                       COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) AS days_on_stock
                        FROM promotion_history ph
                                 JOIN products p ON p.id = ph.product_id
                                 JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                 LEFT JOIN transactions t ON t.location_id = ph.store_id AND t.product_id = ph.product_id
                                     AND t.date BETWEEN ph.start_date AND ph.end_date
                                 LEFT JOIN inventory i ON i.location_id = ph.store_id AND i.product_id = ph.product_id
                                     AND i.date BETWEEN ph.start_date AND ph.end_date
                        WHERE ph.store_id = :storeId
                          AND cc.second_subcategory_id = :secondSubcategoryId
                          AND ph.promotion_type = :promotionType
                          AND ph.start_date >= :lookbackFrom
                          AND ph.start_date < :beforeDate
                          AND ph.id <> :excludeId
                        GROUP BY ph.promotion_id, ph.store_id, ph.product_id, ph.start_date
                        HAVING COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) > 0
                        ORDER BY ph.start_date DESC,
                                 ISNULL(SUM(t.units), 0) * 1.0 / COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.date END) DESC,
                                 ph.promotion_id ASC
                        """)
                .param("storeId", storeId)
                .param("secondSubcategoryId", secondSubcategoryId)
                .param("promotionType", promotionType)
                .param("lookbackFrom", lookbackFrom)
                .param("beforeDate", beforeDate)
                .param("excludeId", excludeId)
                .query(DonorCandidate.class)
                .optional();
    }
}
