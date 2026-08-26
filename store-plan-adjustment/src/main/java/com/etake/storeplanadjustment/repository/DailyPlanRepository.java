package com.etake.storeplanadjustment.repository;

import com.etake.storeplanadjustment.model.DailyPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DailyPlanRepository {
    private final JdbcClient jdbcClient;

    /**
     * Loads the daily plans inside {@code [fromDate, toDate]} for the given stores. Whether a plan
     * is actually adjusted (i.e. its date is disrupted) is decided in the service layer.
     */
    public List<DailyPlan> findPlansToAdjust(final LocalDate fromDate,
                                             final LocalDate toDate,
                                             final Collection<String> storeIds) {
        if (storeIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT p.store_id    AS store_id,
                               p.category_id AS category_id,
                               p.date        AS date,
                               p.turnover    AS turnover,
                               p.margin      AS margin
                        FROM daily_sales_plans p
                        WHERE p.date BETWEEN :fromDate AND :toDate
                          AND p.store_id IN (:storeIds);
                        """)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("storeIds", storeIds)
                .query(DailyPlan.class)
                .list();
    }
}
