package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Counters of sale checks rather than ready-made averages: an average of a region or of the company must be
 * weighted by checks, which is only possible by summing the counters of its stores.
 *
 * @param checks        number of sale checks
 * @param amount        total of the sale checks
 * @param positions     number of distinct products summed over the sale checks
 * @param loyaltyChecks number of sale checks with a client
 * @param loyaltyAmount total of the sale checks with a client
 */
public record StoreChecks(
        String storeName,
        long checks,
        BigDecimal amount,
        long positions,
        long loyaltyChecks,
        BigDecimal loyaltyAmount
) {
    private static final int MONEY_SCALE = 2;
    private static final int ITEMS_SCALE = 4;

    public static StoreChecks empty(final String storeName) {
        return new StoreChecks(storeName, 0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
    }

    /** @return {@code null} when there were no sale checks, the same for the other averages */
    public BigDecimal avgCheck() {
        return average(amount, checks, MONEY_SCALE);
    }

    public BigDecimal loyaltyAvgCheck() {
        return average(loyaltyAmount, loyaltyChecks, MONEY_SCALE);
    }

    public BigDecimal avgItemsPerCheck() {
        return average(BigDecimal.valueOf(positions), checks, ITEMS_SCALE);
    }

    private static BigDecimal average(final BigDecimal total, final long count, final int scale) {
        return count == 0 ? null : total.divide(BigDecimal.valueOf(count), scale, RoundingMode.HALF_UP);
    }
}
