package com.etake.salesplan.model;

import java.time.LocalDate;
import java.util.Map;

public record ForecastStoreCategorySales(
        StoreCategoryKey key,
        StoreCategoryKey similarKey,
        Sales sameMonthLastYear,
        Sales plannedMonthLasYear,
        Sales currentMonth,
        Sales plannedMonth,
        Integer days,
        Sales forecast,
        Map<LocalDate, Sales> dailyShares
) {
}
