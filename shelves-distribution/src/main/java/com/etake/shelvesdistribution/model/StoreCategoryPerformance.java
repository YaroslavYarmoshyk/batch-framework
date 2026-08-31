package com.etake.shelvesdistribution.model;

import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.Comparator;

public record StoreCategoryPerformance(String store, String category, BigDecimal discountSales, BigDecimal costSales,
                                        BigDecimal currentCostBalance) implements Comparable<StoreCategoryPerformance> {

    private static final Comparator<StoreCategoryPerformance> ORDER =
            Comparator.comparing(StoreCategoryPerformance::store).thenComparing(StoreCategoryPerformance::category);

    @Override
    public int compareTo(@NonNull StoreCategoryPerformance other) {
        return ORDER.compare(this, other);
    }
}
