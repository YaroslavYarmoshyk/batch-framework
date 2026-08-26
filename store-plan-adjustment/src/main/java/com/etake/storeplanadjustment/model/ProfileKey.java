package com.etake.storeplanadjustment.model;

import java.time.DayOfWeek;

/**
 * Key for an intraday share profile: the typical within-day turnover distribution of a
 * store/category on a given weekday.
 */
public record ProfileKey(
        String storeId,
        String categoryId,
        DayOfWeek weekday
) {
}
