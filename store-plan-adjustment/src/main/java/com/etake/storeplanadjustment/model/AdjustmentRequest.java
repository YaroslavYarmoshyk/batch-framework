package com.etake.storeplanadjustment.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * A single store/date disruption read from the input file. Rows without a known {@code amount}
 * are normalized to the set of 30-minute slots (keyed by their start {@link LocalTime}) that were
 * lost, and still go through the intraday-share algorithm; rows that do carry a known
 * {@code amount} are excluded from {@code algorithmSlots} and instead contribute directly to
 * {@code amount}, which is subtracted from the plan as-is.
 */
public record AdjustmentRequest(
        String storeName,
        LocalDate date,
        Set<LocalTime> algorithmSlots,
        BigDecimal amount
) {
}
