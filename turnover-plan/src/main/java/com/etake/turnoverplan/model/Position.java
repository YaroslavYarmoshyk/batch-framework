package com.etake.turnoverplan.model;

import java.math.BigDecimal;

public record Position(
        String regionName,
        String storeName,
        String categoryName,
        BigDecimal turnover,
        BigDecimal margin,
        Integer salesDays
) {
    public static Position empty() {
        return new Position(null, null, null, null, null, null);
    }

    public String getKey() {
        return String.format("%s_%s", storeName, categoryName);
    }
}
