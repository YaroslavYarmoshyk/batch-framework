package com.etake.shelvesdistribution.repository;

import com.etake.shelvesdistribution.model.DateRange;
import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

import static com.etake.shelvesdistribution.constants.CommonConstants.ALL_STORES;

@Repository
@RequiredArgsConstructor
public class StoreCategoryRepository {

    private final JdbcClient jdbcClient;

    public List<StoreCategoryPerformance> getStoreCategoryPerformance(List<String> stores) {
        List<String> storeNames = resolveStoreNames(stores);
        if (storeNames.isEmpty()) {
            return List.of();
        }
        DateRange dateRange = getDateRange();
        return jdbcClient.sql(getBaseQuery())
                .param("fromDate", dateRange.from())
                .param("toDate", dateRange.to())
                .param("stores", storeNames)
                .query(StoreCategoryPerformance.class)
                .list();
    }

    public List<StoreCategoryPerformance> getStoreCategoryPerformance(String region) {
        List<String> stores = jdbcClient.sql("""
                        SELECT l.name
                        FROM locations l
                        JOIN regions r ON r.id = l.region_id
                        WHERE r.name = :region
                        """)
                .param("region", region)
                .query(String.class)
                .list();
        return getStoreCategoryPerformance(stores);
    }

    private static String getBaseQuery() {
        return """
                WITH monthly_sales AS (SELECT l.id                                          AS storeId,
                                              l.name                                        AS store,
                                              c.name                                        AS category,
                                              DATEFROMPARTS(YEAR(t.date), MONTH(t.date), 1) AS salesMonth,
                                              SUM(t.units * t.discount_price)               AS discountSales,
                                              SUM(t.units * t.cost_price)                   AS costSales
                                       FROM transactions t
                                                JOIN locations l ON l.id = t.location_id
                                                JOIN products p ON p.id = t.product_id
                                                JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                                JOIN categories c ON c.id = cc.category_id
                                       WHERE t.date BETWEEN :fromDate AND :toDate
                                         AND (l.close_date IS NULL OR l.close_date > :toDate)
                                         AND l.type = 'store'
                                         AND c.category_manager_id <> '000002085'
                                         AND l.name IN (:stores)
                                       GROUP BY l.id, l.name, c.name, DATEFROMPARTS(YEAR(t.date), MONTH(t.date), 1)),
                     balance AS (SELECT l.id                           AS storeId,
                                        c.name                         AS category,
                                        SUM(i.quantity * i.cost_price) AS currentCostBalance
                                 FROM inventory i
                                          JOIN locations l ON l.id = i.location_id
                                          JOIN products p ON p.id = i.product_id
                                          JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                          JOIN categories c ON c.id = cc.category_id
                                 WHERE i.date = :toDate
                                   AND l.type = 'store'
                                   AND l.name IN (:stores)
                                   AND quantity < 1000000
                                 GROUP BY l.id, c.name)
                SELECT m.store,
                       m.category,
                       AVG(CAST(m.discountSales AS decimal(19, 4))) AS discountSales,
                       AVG(CAST(m.costSales AS decimal(19, 4)))     AS costSales,
                       COALESCE(MAX(b.currentCostBalance), 0)       AS currentCostBalance
                FROM monthly_sales m
                         LEFT JOIN balance b ON b.storeId = m.storeId AND b.category = m.category
                GROUP BY m.storeId, m.store, m.category""";
    }

    private DateRange getDateRange() {
        LocalDate toDate = jdbcClient.sql("SELECT max(date) FROM transactions")
                .query(LocalDate.class)
                .optional()
                .orElseThrow(() -> new IllegalStateException("No transactions available"))
                .minusDays(1);
        return new DateRange(toDate.minusYears(1), toDate);
    }

    private List<String> resolveStoreNames(List<String> stores) {
        if (stores == null || stores.isEmpty()) {
            return List.of();
        }
        if (!stores.contains(ALL_STORES)) {
            return stores;
        }
        return jdbcClient.sql("SELECT name FROM locations WHERE type = 'store'")
                .query(String.class)
                .list();
    }
}
