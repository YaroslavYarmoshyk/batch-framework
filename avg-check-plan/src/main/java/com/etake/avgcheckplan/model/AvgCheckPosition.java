package com.etake.avgcheckplan.model;

import java.math.BigDecimal;

public record AvgCheckPosition(
        String region,
        String store,
        BigDecimal avgCheck
) {
}
