package com.etake.storeplanadjustment.model;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * A single store/date disruption read from the input file, already normalized to the set of
 * 30-minute slots (keyed by their start {@link LocalTime}) that were lost.
 */
public record AdjustmentRequest(
        String storeName,
        LocalDate date,
        Set<LocalTime> slots
) {
}
