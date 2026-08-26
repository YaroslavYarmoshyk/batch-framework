package com.etake.turnoverplan.repository.impl;

import com.etake.turnoverplan.model.Position;
import com.etake.turnoverplan.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PositionRepositoryImpl implements PositionRepository {
    private final JdbcClient jdbcClient;

    @Override
    public List<Position> findAllInPeriod(final LocalDate fromDate, final LocalDate toDate, final List<LocalDate> exclusionDates) {
        final StringBuilder query = new StringBuilder(getBaseQuery());
        if (!CollectionUtils.isEmpty(exclusionDates)) {
            query.append(" AND CONVERT(DATE, t.datetime) NOT IN (:exclusionDates) ");
        }
        query.append("""
                    GROUP BY CONVERT(DATE, t.datetime), r.name, l.name, cc.category_name
                ) AS grouped
                GROUP BY region_name, store_name, category_name;
                """);

        return jdbcClient.sql(query.toString())
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("exclusionDates", exclusionDates)
                .query(Position.class)
                .list();
    }

    private static String getBaseQuery() {
        return """
                SELECT grouped.region_name,
                       grouped.store_name,
                       grouped.category_name,
                       AVG(turnover) AS turnover,
                       AVG(margin)   AS margin,
                       COUNT(date)   AS sales_days
                FROM (SELECT CONVERT(DATE, t.datetime)                                AS date,
                             r.name                                                   AS region_name,
                             l.name                                                   AS store_name,
                             cc.category_name,
                             SUM(t.units * t.discount_price)                          AS turnover,
                             SUM(t.units * t.discount_price - t.units * t.cost_price) AS margin
                      FROM transactions t
                               LEFT JOIN locations l ON l.id = t.location_id
                               LEFT JOIN regions r ON r.id = l.region_id
                               LEFT JOIN products p ON p.id = t.product_id
                               LEFT JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                      WHERE t.datetime >= :fromDate
                        AND t.datetime < DATEADD(DAY, 1, :toDate)
                        AND l.close_date is NULL
                """;
    }
}
