package com.etake.storeplanadjustment.repository;

import com.etake.storeplanadjustment.model.SlotTurnover;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import static com.etake.storeplanadjustment.utils.Constants.SLOT_MINUTES;

@Repository
@RequiredArgsConstructor
public class TransactionSlotRepository {
    private final JdbcClient jdbcClient;

    /**
     * Returns, per store/category/date, the turnover ({@code SUM(units * discount_price)}) aggregated
     * into 30-minute slots. Used both to build the historical share profile and to read the actual
     * sales that occurred inside a disrupted window. Read-only.
     */
    public List<SlotTurnover> findSlotTurnover(final Collection<LocalDate> dates,
                                               final Collection<String> storeIds) {
        if (dates.isEmpty() || storeIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT CONVERT(DATE, t.datetime)                                       AS date,
                               l.id                                                            AS store_id,
                               cc.category_id                                                  AS category_id,
                               DATEPART(HOUR, t.datetime)                                      AS slot_hour,
                               CASE WHEN DATEPART(MINUTE, t.datetime) < 30 THEN 0 ELSE 1 END   AS slot_half,
                               SUM(t.units * t.discount_price)                                 AS turnover
                        FROM transactions t
                                 JOIN locations l ON l.id = t.location_id
                                 JOIN products p ON p.id = t.product_id
                                 JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                        WHERE CONVERT(DATE, t.datetime) IN (:dates)
                          AND l.id IN (:storeIds)
                        GROUP BY CONVERT(DATE, t.datetime),
                                 l.id,
                                 cc.category_id,
                                 DATEPART(HOUR, t.datetime),
                                 CASE WHEN DATEPART(MINUTE, t.datetime) < 30 THEN 0 ELSE 1 END;
                        """)
                .param("dates", dates)
                .param("storeIds", storeIds)
                .query((rs, rowNum) -> new SlotTurnover(
                        rs.getDate("date").toLocalDate(),
                        rs.getString("store_id"),
                        rs.getString("category_id"),
                        LocalTime.of(rs.getInt("slot_hour"), rs.getInt("slot_half") * SLOT_MINUTES),
                        rs.getBigDecimal("turnover")
                ))
                .list();
    }
}
