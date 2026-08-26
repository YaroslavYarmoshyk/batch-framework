package com.etake.avgcheckplan.model;

import java.math.BigDecimal;

public record ForecastPosition(
        String region,
        String store,
        String similarStore,
        BigDecimal prevYearAvgCheckToDate,
        BigDecimal prevYearAvgCheckLastDay,
        BigDecimal dynamic,
        BigDecimal currYearAvgCheckToDate,
        BigDecimal forecastedAvgCheckLastDay,
        BigDecimal currentAvgCheckLastDay,
        BigDecimal fulfilment,
        BigDecimal adjustedForecastedAvgCheckLastDay,
        BigDecimal adjustedFulfilment
) {
}
