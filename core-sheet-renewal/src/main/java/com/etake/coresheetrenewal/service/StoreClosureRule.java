package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.model.StoreLocation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * When a store counts as closed by the start of a new month. {@code close_date} is often set late, so a store without
 * sales on the last days of the finished month counts as closed too, unless it has a plan for the new month: then it
 * is only closed temporarily. A set {@code close_date} is final, whatever the plan says.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StoreClosureRule {
    public static final int DAYS_WITHOUT_SALES_TO_CLOSE = 2;

    /** @param location {@code null} for a store unknown to the database, which cannot be judged closed */
    public static boolean isClosed(final StoreLocation location, final Map<LocalDate, BigDecimal> dailySales,
                                   final LocalDate monthStart, final boolean plannedForNewMonth) {
        if (location == null) {
            return false;
        }
        return location.closedBefore(monthStart) || noSalesOnLastDays(dailySales, monthStart) && !plannedForNewMonth;
    }

    public static boolean noSalesOnLastDays(final Map<LocalDate, BigDecimal> dailySales, final LocalDate monthStart) {
        return lastDays(monthStart).stream()
                .noneMatch(day -> dailySales.getOrDefault(day, BigDecimal.ZERO).signum() > 0);
    }

    public static List<LocalDate> lastDays(final LocalDate monthStart) {
        return IntStream.rangeClosed(1, DAYS_WITHOUT_SALES_TO_CLOSE).mapToObj(monthStart::minusDays).toList();
    }
}
