package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.model.AbcCategory;
import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreChecks;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/** Adds up the monthly actuals of stores for a region or the company. */
@Service
public class MonthlyTotalsService {

    /** @param name the region or company the sum is for */
    public MonthlyTotals sum(final String name, final Collection<MonthlyTotals> totals) {
        return totals.stream().reduce(MonthlyTotals.empty(name), this::add);
    }

    private MonthlyTotals add(final MonthlyTotals left, final MonthlyTotals right) {
        return new MonthlyTotals(
                left.margin().add(right.margin()),
                add(left.checks(), right.checks()),
                add(left.stock(), right.stock()));
    }

    private StoreChecks add(final StoreChecks left, final StoreChecks right) {
        return new StoreChecks(left.storeName(),
                left.checks() + right.checks(),
                left.amount().add(right.amount()),
                left.positions() + right.positions(),
                left.loyaltyChecks() + right.loyaltyChecks(),
                left.loyaltyAmount().add(right.loyaltyAmount()));
    }

    private Map<AbcCategory, BigDecimal> add(final Map<AbcCategory, BigDecimal> left, final Map<AbcCategory, BigDecimal> right) {
        final Map<AbcCategory, BigDecimal> sum = new EnumMap<>(AbcCategory.class);
        sum.putAll(left);
        right.forEach((category, amount) -> sum.merge(category, amount, BigDecimal::add));
        return sum;
    }
}
