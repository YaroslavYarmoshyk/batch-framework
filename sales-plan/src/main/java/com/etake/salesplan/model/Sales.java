package com.etake.salesplan.model;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Collection;
import java.util.stream.Stream;

public record Sales(BigDecimal turnover, BigDecimal margin) {

    public static Sales zero() {
        return new Sales(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public Sales add(Sales other) {
        return new Sales(
                turnover.add(other.turnover),
                margin.add(other.margin)
        );
    }

    public Sales multiply(BigDecimal coefficient) {
        return new Sales(
                turnover.multiply(coefficient),
                margin.multiply(coefficient)
        );
    }

    public Sales divide(BigDecimal divisor, MathContext mc) {
        return new Sales(
                turnover.divide(divisor, mc),
                margin.divide(divisor, mc)
        );
    }

    public static Sales sum(Stream<Sales> sales) {
        return sales.reduce(zero(), Sales::add);
    }

    public static Sales average(Collection<Sales> sales, MathContext mc) {
        if (sales.isEmpty()) {
            return zero();
        }

        Sales sum = sum(sales.stream());
        return sum.divide(BigDecimal.valueOf(sales.size()), mc);
    }
}
