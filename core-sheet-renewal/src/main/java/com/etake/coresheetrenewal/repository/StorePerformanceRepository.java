package com.etake.coresheetrenewal.repository;

import com.etake.coresheetrenewal.model.RegionOrder;
import com.etake.coresheetrenewal.model.StoreCheckPlan;
import com.etake.coresheetrenewal.model.StoreChecks;
import com.etake.coresheetrenewal.model.StoreDailySales;
import com.etake.coresheetrenewal.model.StoreFirstSale;
import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StoreSalesPlan;
import com.etake.coresheetrenewal.model.StoreStock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StorePerformanceRepository {
    /** A store without sales for this long before a month counts as opening in it. */
    private static final int OPENING_LOOKBACK_MONTHS = 3;
    /** Sales history used to split the assortment of a store into A, B and C. */
    private static final int ABC_WINDOW_MONTHS = 3;

    private final JdbcClient jdbcClient;

    public List<StoreLocation> getStoreLocations() {
        return jdbcClient.sql("""
                        SELECT l.name       AS store_name,
                               r.name       AS region_name,
                               l.close_date AS close_date
                        FROM locations l
                                 LEFT JOIN regions r ON r.id = l.region_id
                        WHERE l.type = 'store'
                        """)
                .query(StoreLocation.class)
                .list();
    }

    public List<RegionOrder> getRegionOrders() {
        return jdbcClient.sql("""
                        SELECT r.name       AS name,
                               r.sort_order AS sort_order
                        FROM regions r
                        WHERE r.sort_order IS NOT NULL
                        """)
                .query(RegionOrder.class)
                .list();
    }

    /** The first sale of each store selling in the month, looking back only as far as it takes to tell opening stores. */
    public List<StoreFirstSale> getFirstSales(final YearMonth month) {
        return jdbcClient.sql("""
                        SELECT l.name        AS store_name,
                               MIN(t.[date]) AS first_sale_date
                        FROM transactions t
                                 JOIN locations l ON l.id = t.location_id
                        WHERE t.[date] BETWEEN :from AND :to
                          AND t.units > 0
                          AND l.type = 'store'
                        GROUP BY l.name
                        """)
                .param("from", month.minusMonths(OPENING_LOOKBACK_MONTHS).atDay(1))
                .param("to", month.atEndOfMonth())
                .query(StoreFirstSale.class)
                .list();
    }

    public List<StoreDailySales> getDailySales(final YearMonth month) {
        return jdbcClient.sql("""
                        SELECT l.name                                                     AS store_name,
                               t.[date]                                                   AS date,
                               COALESCE(SUM(t.units * t.discount_price), 0)               AS sales,
                               COALESCE(SUM(t.units * (t.discount_price - t.cost_price)), 0) AS margin
                        FROM transactions t
                                 JOIN locations l ON l.id = t.location_id
                        WHERE t.[date] BETWEEN :from AND :to
                          AND l.type = 'store'
                        GROUP BY l.name, t.[date]
                        """)
                .param("from", month.atDay(1))
                .param("to", month.atEndOfMonth())
                .query(StoreDailySales.class)
                .list();
    }

    /** Only sale lines count ({@code units > 0}), so a refund affects neither the averages nor the check count. */
    public List<StoreChecks> getSaleChecks(final YearMonth month) {
        return jdbcClient.sql("""
                        WITH sale_checks AS (
                            SELECT t.location_id,
                                   SUM(t.units * t.discount_price)                  AS amount,
                                   COUNT(DISTINCT t.product_id)                     AS positions,
                                   MAX(CASE WHEN t.client_id IS NULL THEN 0 ELSE 1 END) AS has_client
                            FROM transactions t
                            WHERE t.[date] BETWEEN :from AND :to
                              AND t.units > 0
                            GROUP BY t.location_id, t.[date], t.check_id
                        )
                        SELECT l.name                   AS store_name,
                               COUNT(*)                 AS checks,
                               SUM(c.amount)            AS amount,
                               SUM(c.positions)         AS positions,
                               SUM(c.has_client)        AS loyalty_checks,
                               SUM(c.has_client * c.amount) AS loyalty_amount
                        FROM sale_checks c
                                 JOIN locations l ON l.id = c.location_id
                        WHERE l.type = 'store'
                        GROUP BY l.name
                        """)
                .param("from", month.atDay(1))
                .param("to", month.atEndOfMonth())
                .query(StoreChecks.class)
                .list();
    }

    public List<StoreSalesPlan> getSalesPlans(final YearMonth month) {
        return jdbcClient.sql("""
                        SELECT l.name                       AS store_name,
                               COALESCE(SUM(p.turnover), 0) AS turnover,
                               COUNT(DISTINCT p.[date])     AS plan_days
                        FROM daily_sales_plans p
                                 JOIN locations l ON l.id = p.store_id
                        WHERE p.[date] BETWEEN :from AND :to
                          AND l.type = 'store'
                        GROUP BY l.name
                        """)
                .param("from", month.atDay(1))
                .param("to", month.atEndOfMonth())
                .query(StoreSalesPlan.class)
                .list();
    }

    public List<StoreCheckPlan> getCheckPlans(final YearMonth month) {
        return jdbcClient.sql("""
                        SELECT l.name                AS store_name,
                               p.avg_check           AS avg_check,
                               p.avg_items_per_check AS avg_items_per_check
                        FROM store_checks_plans p
                                 JOIN locations l ON l.id = p.store_id
                        WHERE p.year = :year
                          AND p.month = :month
                          AND (l.close_date IS NULL OR l.close_date >= :from)
                        """)
                .param("year", month.getYear())
                .param("month", month.getMonthValue())
                .param("from", month.atDay(1))
                .query(StoreCheckPlan.class)
                .list();
    }

    /**
     * Stock at purchase prices at the end of the month, split per store by each product's cumulative share of the
     * store's turnover over the last months: A up to 80%, B up to 95%, C the rest, D no sales (dead stock).
     */
    public List<StoreStock> getAbcStock(final YearMonth month) {
        final LocalDate monthEnd = month.atEndOfMonth();
        return jdbcClient.sql("""
                        WITH product_sales AS (
                            SELECT t.location_id,
                                   t.product_id,
                                   SUM(t.units * t.discount_price) AS sales
                            FROM transactions t
                            WHERE t.[date] BETWEEN :salesFrom AND :monthEnd
                            GROUP BY t.location_id, t.product_id
                            HAVING SUM(t.units * t.discount_price) > 0
                        ),
                        product_share AS (
                            SELECT location_id,
                                   product_id,
                                   SUM(sales) OVER (PARTITION BY location_id ORDER BY sales DESC, product_id ROWS UNBOUNDED PRECEDING)
                                       / SUM(sales) OVER (PARTITION BY location_id) AS cumulative_share
                            FROM product_sales
                        )
                        SELECT l.name AS store_name,
                               CASE
                                   WHEN s.product_id IS NULL THEN 'D'
                                   WHEN s.cumulative_share <= 0.80 THEN 'A'
                                   WHEN s.cumulative_share <= 0.95 THEN 'B'
                                   ELSE 'C'
                               END AS abc_category,
                               SUM(i.quantity * i.cost_price) AS amount
                        FROM inventory i
                                 JOIN locations l ON l.id = i.location_id
                                 LEFT JOIN product_share s ON s.location_id = i.location_id AND s.product_id = i.product_id
                        WHERE i.[date] = :monthEnd
                          AND l.type = 'store'
                          AND i.quantity < 1000000
                        GROUP BY l.name,
                                 CASE
                                     WHEN s.product_id IS NULL THEN 'D'
                                     WHEN s.cumulative_share <= 0.80 THEN 'A'
                                     WHEN s.cumulative_share <= 0.95 THEN 'B'
                                     ELSE 'C'
                                 END
                        """)
                .param("salesFrom", month.minusMonths(ABC_WINDOW_MONTHS - 1L).atDay(1))
                .param("monthEnd", monthEnd)
                .query(StoreStock.class)
                .list();
    }
}
