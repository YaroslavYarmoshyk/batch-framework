package com.etake.avgcheckplan.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

public record SeasonalCoefficient(
        @DefaultValue("0.05")
        BigDecimal divergenceThreshold,
        @DefaultValue("0.6")
        BigDecimal recentYearWeight,
        @DefaultValue("0.02")
        BigDecimal growth
) {
}
