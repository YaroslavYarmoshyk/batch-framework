package com.etake.promotionforecast.repository;

import com.etake.promotionforecast.model.PromotionPosition;
import com.etake.promotionforecast.model.enumeration.PromotionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PromotionPositionsRepository {
    private final JdbcClient jdbcClient;

    public boolean existsPromotion(PromotionType promotionType, LocalDate startDate, LocalDate endDate) {
        return jdbcClient.sql("""
                        SELECT CASE WHEN EXISTS (
                            SELECT 1
                            FROM promotion_history
                            WHERE promotion_type = :promotionType
                              AND start_date = :startDate
                              AND end_date = :endDate
                        ) THEN 1 ELSE 0 END
                        """)
                .param("promotionType", promotionType.getName())
                .param("startDate", startDate)
                .param("endDate", endDate)
                .query(Integer.class)
                .single() == 1;
    }

    public List<PromotionPosition> getPromotionPositions(PromotionType promotionType, LocalDate from, LocalDate to) {
        jdbcClient.sql("DROP TABLE IF EXISTS #promo, #fmt, #win, #sales, #stock").update();

        return jdbcClient.sql("""
                         DECLARE @from          DATE          = :from;
                         DECLARE @to            DATE          = :to
                         DECLARE @promotionType NVARCHAR(255) = :promotionType
                        \s
                         -- 1. Потрібні акції + перша підкатегорія товару
                         SELECT ph.id,
                                                             ph.store_id,
                                                             ph.product_id,
                                                             cc.first_subcategory_id,
                                                             cc.third_subcategory_id,
                                                             cc.third_subcategory_name,
                                                             cc.category_id,
                                                             ph.carryover,
                                                             ph.start_date,
                                                             ph.end_date
                                                      INTO #promo
                                                      FROM promotion_history ph
                                                               JOIN locations l
                                                                    ON l.id = ph.store_id
                                                                        AND l.close_date IS NULL
                                                               LEFT JOIN products p
                                                                         ON p.id = ph.product_id
                                                               LEFT JOIN category_classification cc
                                                                         ON cc.third_subcategory_id = p.subcategory_id_3
                                                      WHERE ph.promotion_type = @promotionType
                                                        AND ph.start_date >= @from
                                                        AND ph.end_date IS NOT NULL
                                                        AND ph.end_date <= @to;
                                                     \s
                                                      -- 1b. Формат магазину на дату початку акції
                                                      WITH keys AS (
                                                          SELECT DISTINCT store_id, first_subcategory_id, start_date
                                                          FROM #promo
                                                      ),
                                                           cand AS (
                                                               SELECT k.store_id,
                                                                      k.first_subcategory_id,
                                                                      k.start_date,
                                                                      sfh.format,
                                                                      ROW_NUMBER() OVER (PARTITION BY k.store_id, k.first_subcategory_id, k.start_date
                                                                          ORDER BY sfh.[date] DESC) AS rn
                                                               FROM keys k
                                                                        JOIN store_format_history sfh
                                                                             ON sfh.store_id = k.store_id
                                                                                 AND sfh.first_subcategory_id = k.first_subcategory_id
                                                                                 AND sfh.type = 'store'
                                                                                 AND sfh.[date] <= k.start_date
                                                           )
                                                      SELECT store_id, first_subcategory_id, start_date, format
                                                      INTO #fmt
                                                      FROM cand
                                                      WHERE rn = 1;
                                                     \s
                                                      -- 2. Унікальні вікна «товар + період»
                                                      SELECT DISTINCT product_id, start_date, end_date
                                                      INTO #win
                                                      FROM #promo;
                                                     \s
                                                      CREATE CLUSTERED INDEX cx ON #win (product_id, start_date, end_date);
                                                     \s
                                                      -- 3. Продажі
                                                      SELECT w.product_id,
                                                             w.start_date,
                                                             w.end_date,
                                                             t.location_id,
                                                             SUM(t.units) AS sold_units
                                                      INTO #sales
                                                      FROM #win w
                                                               JOIN transactions t
                                                                    ON t.product_id = w.product_id
                                                                        AND t.[date] >= w.start_date
                                                                        AND t.[date] <= w.end_date
                                                      GROUP BY w.product_id, w.start_date, w.end_date, t.location_id;
                                                     \s
                                                      -- 4. Дні на залишку
                                                      SELECT w.product_id,
                                                             w.start_date,
                                                             w.end_date,
                                                             i.location_id,
                                                             COUNT(*) AS days_on_stock
                                                      INTO #stock
                                                      FROM #win w
                                                               JOIN inventory i
                                                                    ON i.product_id = w.product_id
                                                                        AND i.[date] >= w.start_date
                                                                        AND i.[date] <= w.end_date
                                                      GROUP BY w.product_id, w.start_date, w.end_date, i.location_id;
                                                     \s
                                                      -- 5. Результат
                                                      SELECT pr.id AS promotion_id,
                                                             CASE WHEN e.id IS NOT NULL
                                                                  THEN CONCAT(e.first_name, ' ', e.last_name) END AS manager,
                                                             l.id                        AS store_id,
                                                             l.name                      AS store_name,
                                                             ISNULL(f.format, 0)         AS store_format,
                                                             p.id                        AS product_id,
                                                             p.name                      AS product_name,
                                                             pr.third_subcategory_id     AS third_subcategory_id,
                                                             pr.third_subcategory_name   AS third_subcategory_name,
                                                             ISNULL(pr.carryover, 0)     AS carryover,
                                                             pr.start_date,
                                                             pr.end_date,
                                                             ISNULL(s.sold_units, 0)     AS sold_units,
                                                             ISNULL(st.days_on_stock, 0) AS days_on_stock
                                                      FROM #promo pr
                                                               JOIN locations l ON l.id = pr.store_id
                                                               JOIN products p ON p.id = pr.product_id
                                                               LEFT JOIN categories c ON c.id = pr.category_id
                                                               LEFT JOIN employees e ON e.id = c.category_manager_id
                                                               LEFT JOIN #fmt f
                                                                         ON f.store_id = pr.store_id
                                                                             AND f.first_subcategory_id = pr.first_subcategory_id
                                                                             AND f.start_date = pr.start_date
                                                               LEFT JOIN #sales s
                                                                         ON s.product_id = pr.product_id
                                                                             AND s.location_id = pr.store_id
                                                                             AND s.start_date = pr.start_date
                                                                             AND s.end_date = pr.end_date
                                                               LEFT JOIN #stock st
                                                                         ON st.product_id = pr.product_id
                                                                             AND st.location_id = pr.store_id
                                                                             AND st.start_date = pr.start_date
                                                                             AND st.end_date = pr.end_date;
                           \s"""
                ).param("from", from)
                .param("to", to)
                .param("promotionType", promotionType.getName())
                .query(PromotionPosition.class)
                .list();
    }
}
