package com.etake.storeplanadjustment.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A {@code daily_sales_plans} row that is a candidate for adjustment.
 */
public record DailyPlan(
        String storeId,
        String categoryId,
        LocalDate date,
        BigDecimal turnover,
        BigDecimal margin
) {
}
