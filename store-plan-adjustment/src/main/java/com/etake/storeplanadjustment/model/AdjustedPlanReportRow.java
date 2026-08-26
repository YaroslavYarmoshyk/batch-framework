package com.etake.storeplanadjustment.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One output row: the original plan plus its adjusted turnover.
 */
public record AdjustedPlanReportRow(
        LocalDate date,
        String store,
        String category,
        BigDecimal turnover,
        BigDecimal margin,
        BigDecimal adjustedTurnover
) {
}
