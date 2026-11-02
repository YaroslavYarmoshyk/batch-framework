package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** The monthly actuals that exist on store, region and company rows alike and add up between them. */
public record MonthlyTotals(BigDecimal margin, StoreChecks checks, Map<AbcCategory, BigDecimal> stock) {

    public static MonthlyTotals empty(final String name) {
        return new MonthlyTotals(BigDecimal.ZERO, StoreChecks.empty(name), Map.of());
    }

    public MonthlyTotals plus(final MonthlyTotals other) {
        final Map<AbcCategory, BigDecimal> summedStock = new EnumMap<>(AbcCategory.class);
        summedStock.putAll(stock);
        other.stock.forEach((category, amount) -> summedStock.merge(category, amount, BigDecimal::add));
        return new MonthlyTotals(margin.add(other.margin), checks.plus(other.checks), summedStock);
    }
}
