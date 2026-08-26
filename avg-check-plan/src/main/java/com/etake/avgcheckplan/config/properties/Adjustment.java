package com.etake.avgcheckplan.config.properties;

import java.math.BigDecimal;

public record Adjustment(
        BigDecimal smoothingFactor,
        BigDecimal strengthFactor,
        BigDecimal minLimit,
        BigDecimal maxLimit,
        BigDecimal growth
) {
}
