package com.etake.avgcheckplan.model;

import java.math.BigDecimal;

public record SeasonalPlanPosition(
        String region,
        String store,
        String similarStore,
        BigDecimal baseAvgCheck,
        BigDecimal k1,
        BigDecimal k2,
        BigDecimal appliedCoefficient,
        BigDecimal plannedAvgCheck,
        boolean stableSeasonality,
        boolean singleYearCoefficient
) {
}
