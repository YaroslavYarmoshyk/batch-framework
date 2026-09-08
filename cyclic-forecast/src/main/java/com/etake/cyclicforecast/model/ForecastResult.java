package com.etake.cyclicforecast.model;

import java.math.BigDecimal;
import java.util.List;

public record ForecastResult(
        PlanningScopeRow row,
        AlgorithmLevel level,
        BigDecimal avgDailySales,
        DonorKey donorKey,
        List<String> diagnostics
) {
    public static ForecastResult noData(final PlanningScopeRow row, final List<String> diagnostics) {
        return new ForecastResult(row, AlgorithmLevel.NONE, null, null, diagnostics);
    }
}
