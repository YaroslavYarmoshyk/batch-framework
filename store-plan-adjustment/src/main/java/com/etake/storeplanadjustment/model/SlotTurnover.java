package com.etake.storeplanadjustment.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Turnover of one store/category on one date within one 30-minute slot, as returned by the
 * transactions query. {@code slot} is the slot start time (e.g. {@code 19:30}).
 */
public record SlotTurnover(
        LocalDate date,
        String storeId,
        String categoryId,
        LocalTime slot,
        BigDecimal turnover
) {
}
