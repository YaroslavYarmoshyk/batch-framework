package com.etake.cyclicforecast.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class AvgDailySalesCalculationService {
    private static final int SCALE = 2;

    /**
     * {@code totalUnits / daysOnStock}, or empty when there are no qualifying stock days
     * (decision #2: zero-stock candidates are disqualified, never reported as average daily sales 0).
     */
    public Optional<BigDecimal> computeIfPossible(final long totalUnits, final long daysOnStock) {
        if (daysOnStock <= 0) {
            return Optional.empty();
        }
        return Optional.of(compute(totalUnits, daysOnStock));
    }

    public BigDecimal compute(final long totalUnits, final long daysOnStock) {
        return BigDecimal.valueOf(totalUnits).divide(BigDecimal.valueOf(daysOnStock), SCALE, RoundingMode.HALF_UP);
    }
}
