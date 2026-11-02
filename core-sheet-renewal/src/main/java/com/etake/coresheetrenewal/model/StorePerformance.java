package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * @param salesPlan {@code null} when the store has no turnover plan for the month
 * @param plan      {@code null} when the store has no check plan for the month
 */
public record StorePerformance(
        String storeName,
        Map<LocalDate, BigDecimal> dailySales,
        BigDecimal salesPlan,
        MonthlyTotals totals,
        StoreCheckPlan plan
) {
    public boolean soldOn(final LocalDate date) {
        return dailySales.getOrDefault(date, BigDecimal.ZERO).signum() > 0;
    }

    /** A day counts only with positive sales: a day of nothing but refunds is not a working day. */
    public int workedDays() {
        return workedDays(dailySales);
    }

    public static int workedDays(final Map<LocalDate, BigDecimal> dailySales) {
        return (int) dailySales.values().stream().filter(sales -> sales.signum() > 0).count();
    }
}
