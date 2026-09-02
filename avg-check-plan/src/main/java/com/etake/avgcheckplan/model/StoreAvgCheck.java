package com.etake.avgcheckplan.model;

import java.math.BigDecimal;

/**
 * Bridges {@link ForecastPosition} and {@link SeasonalPlanPosition} into one shape for the
 * {@code store_checks_plans} upload path.
 */
public record StoreAvgCheck(
        String store,
        BigDecimal avgCheck
) {
}
