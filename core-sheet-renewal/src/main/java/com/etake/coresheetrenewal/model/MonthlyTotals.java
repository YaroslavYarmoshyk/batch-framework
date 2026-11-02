package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.util.Map;

/** The monthly actuals that exist on store, region and company rows alike and add up between them. */
public record MonthlyTotals(BigDecimal margin, StoreChecks checks, Map<AbcCategory, BigDecimal> stock) {

    public static MonthlyTotals empty(final String name) {
        return new MonthlyTotals(BigDecimal.ZERO, StoreChecks.empty(name), Map.of());
    }
}
