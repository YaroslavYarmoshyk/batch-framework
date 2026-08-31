package com.etake.shelvesdistribution.model;

import java.math.BigDecimal;

public record CategoryPerformance(String category, BigDecimal discountSales, BigDecimal costSales,
                                  BigDecimal currentCostBalance) implements Comparable<CategoryPerformance> {

    @Override
    public int compareTo(CategoryPerformance other) {
        return category.compareTo(other.category);
    }
}
