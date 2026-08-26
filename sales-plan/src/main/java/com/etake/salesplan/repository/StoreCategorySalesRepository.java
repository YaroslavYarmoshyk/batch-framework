package com.etake.salesplan.repository;

import com.etake.salesplan.model.CategoryCatalogEntry;
import com.etake.salesplan.model.NewStoreCandidate;
import com.etake.salesplan.model.StoreCategorySalesRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StoreCategorySalesRepository {
    private final JdbcClient jdbcClient;

    public List<StoreCategorySalesRecord> getSalesRecords(YearMonth yearMonth) {
        return jdbcClient.sql("""
                        DECLARE @year int = :year;
                        DECLARE @month int = :month;
                        
                        DECLARE @startDate date = DATEFROMPARTS(@year, @month, 1);
                        DECLARE @endDate date = EOMONTH(@startDate);
                        
                        SELECT r.id                                             as region_id,
                               l.id                                             AS store_id,
                               l.name                                           AS store_name,
                               cc.category_id,
                               cc.category_name,
                        
                               DATEPART(DAY, t.[date])                          AS day_of_month,
                        
                               SUM(t.units * t.discount_price)                  AS turnover,
                               SUM(t.units * (t.discount_price - t.cost_price)) AS margin
                        
                        FROM transactions t
                                 JOIN locations l ON t.location_id = l.id
                                 JOIN regions r on r.id = l.region_id
                                 JOIN products p ON t.product_id = p.id
                                 JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                 JOIN categories c ON cc.category_id = c.id
                                 JOIN employees e ON e.id = c.category_manager_id
                        WHERE t.[date] between @startDate and @endDate
                          AND (l.close_date IS NULL OR l.close_date >= @endDate)
                          AND l.type = 'store'
                          AND e.first_name <> N'БезМенеджера'
                        GROUP BY r.id, l.id, l.name,
                                 cc.category_id, cc.category_name,
                                 DATEPART(DAY, t.[date]);
                        """)
                .param("year", yearMonth.getYear())
                .param("month", yearMonth.getMonthValue())
                .query(StoreCategorySalesRecord.class)
                .list();

    }

    public List<NewStoreCandidate> getNewStoreCandidates(YearMonth current) {
        return jdbcClient.sql("""
                        DECLARE @year int = :year;
                        DECLARE @month int = :month;

                        DECLARE @currentStart date = DATEFROMPARTS(@year, @month, 1);
                        DECLARE @endDate date = EOMONTH(@currentStart);

                        SELECT r.id                                                      AS region_id,
                               l.id                                                       AS store_id,
                               l.name                                                     AS store_name,
                               l.open_date,

                               COALESCE(SUM(t.units * t.discount_price), 0)               AS turnover,
                               COALESCE(SUM(t.units * (t.discount_price - t.cost_price)), 0) AS margin,
                               COUNT(DISTINCT t.[date])                                   AS trading_days

                        FROM locations l
                                 JOIN regions r ON r.id = l.region_id
                                 LEFT JOIN transactions t ON t.location_id = l.id AND t.[date] BETWEEN l.open_date AND @endDate
                        WHERE l.open_date >= @currentStart
                          AND (l.close_date IS NULL OR l.close_date >= @endDate)
                          AND l.type = 'store'
                        GROUP BY r.id, l.id, l.name, l.open_date;
                        """)
                .param("year", current.getYear())
                .param("month", current.getMonthValue())
                .query(NewStoreCandidate.class)
                .list();
    }

    public List<CategoryCatalogEntry> getCategoryCatalog() {
        return jdbcClient.sql("""
                        SELECT DISTINCT cc.category_id, cc.category_name
                        FROM category_classification cc
                                 JOIN categories c ON cc.category_id = c.id
                                 JOIN employees e ON e.id = c.category_manager_id
                        WHERE e.first_name <> N'БезМенеджера';
                        """)
                .query(CategoryCatalogEntry.class)
                .list();
    }
}
