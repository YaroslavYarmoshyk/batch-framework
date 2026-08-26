package com.etake.storeplanadjustment.model;

import java.time.LocalDate;

/**
 * Grouping key for slot turnover: one store/category on one calendar date.
 */
public record StoreCategoryDate(
        String storeId,
        String categoryId,
        LocalDate date
) {
}
