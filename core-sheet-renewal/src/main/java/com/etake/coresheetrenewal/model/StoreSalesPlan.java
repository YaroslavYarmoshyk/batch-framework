package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** @param planDays number of days the plan was set for */
public record StoreSalesPlan(String storeName, BigDecimal turnover, int planDays) {
    private static final int MONEY_SCALE = 2;

    /** The plan of the days the store really worked; a store that worked all the planned days keeps its plan as is. */
    public BigDecimal turnoverFor(final int workedDays) {
        if (workedDays >= planDays) {
            return turnover;
        }
        return turnover.multiply(BigDecimal.valueOf(workedDays))
                .divide(BigDecimal.valueOf(planDays), MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
