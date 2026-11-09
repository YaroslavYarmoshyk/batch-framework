package com.etake.salesplan.utils;

import com.etake.salesplan.model.Sales;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DailyShareUtils {

    /**
     * Splits a month equally across its non-holiday dates. If every date is a holiday, splits across
     * the whole month so that the planned amount is never lost.
     */
    public static Map<LocalDate, Sales> flatDailyShares(YearMonth month, List<LocalDate> holidays) {
        List<LocalDate> tradingDates = new ArrayList<>();
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            if (!holidays.contains(date)) {
                tradingDates.add(date);
            }
        }
        if (tradingDates.isEmpty()) {
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                tradingDates.add(month.atDay(day));
            }
        }

        BigDecimal share = BigDecimal.ONE.divide(BigDecimal.valueOf(tradingDates.size()), PRECISE_MATH_CONTEXT);
        Map<LocalDate, Sales> shares = new TreeMap<>();
        for (LocalDate date : tradingDates) {
            shares.put(date, new Sales(share, share));
        }
        return shares;
    }
}
