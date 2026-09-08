package com.etake.cyclicforecast.repository;

import com.etake.cyclicforecast.model.PlanningScopeRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlanningScopeRepository {
    private final JdbcClient jdbcClient;

    public List<PlanningScopeRow> loadPlanningScope(final LocalDate fromDate, final LocalDate toDate, final String promotionType) {
        return jdbcClient.sql("""
                        SELECT ph.id             AS id,
                               ph.promotion_id   AS promotion_id,
                               ph.store_id       AS store_id,
                               ph.product_id     AS product_id,
                               ph.promotion_type AS promotion_type,
                               ph.store_format   AS store_format,
                               ph.start_date     AS start_date,
                               ph.carryover      AS carryover
                        FROM promotion_history ph
                        WHERE ph.start_date BETWEEN :fromDate AND :toDate
                          AND ph.promotion_type = :promotionType
                        ORDER BY ph.store_id, ph.product_id
                        """)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("promotionType", promotionType)
                .query(PlanningScopeRow.class)
                .list();
    }
}
