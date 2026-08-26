package com.etake.salesplan.service;

import com.etake.salesplan.config.YearMonthProperties;
import com.etake.salesplan.model.ForecastStoreCategorySales;
import com.etake.salesplan.model.Sales;
import com.etake.salesplan.model.SimilarStoreCategoryAveragedSales;
import com.etake.salesplan.model.StoreCategoryKey;
import com.etake.salesplan.model.enumeration.Period;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;
import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class ForecastService {
    private final YearMonthProperties yearMonthProperties;
    private final HolidaysService holidaysService;

    public List<ForecastStoreCategorySales> getForecastStoreCategorySales(List<SimilarStoreCategoryAveragedSales> storeCategorySales,
                                                                          Map<StoreCategoryKey, Map<LocalDate, Sales>> plannedMonthLastYearDailySales) {
        Map<String, List<LocalDate>> holidays = holidaysService.getHolidays();
        List<ForecastStoreCategorySales> result = new ArrayList<>();
        for (SimilarStoreCategoryAveragedSales storeCategorySale : storeCategorySales) {
            Map<Period, Sales> salesByPeriod = storeCategorySale.similarSales();
            Sales sameMonthLastYear = salesByPeriod.get(Period.SAME_MONTH_LAST_YEAR);
            Sales plannedMonthLastYear = salesByPeriod.get(Period.PLANNED_MONTH_LAST_YEAR);
            Sales currentMonth = salesByPeriod.get(Period.CURRENT_MONTH);
            BigDecimal plannedMonthTurnover = calculateTurnoverForecast(sameMonthLastYear, plannedMonthLastYear, currentMonth, Sales::turnover);
            BigDecimal plannedMonthMargin = calculateMarginForecast(sameMonthLastYear, plannedMonthLastYear, currentMonth, plannedMonthTurnover);
            plannedMonthMargin = clampMargin(plannedMonthMargin, plannedMonthTurnover);
            Sales plannedMonth = new Sales(plannedMonthTurnover, plannedMonthMargin);

            StoreCategoryKey storeCategoryKey = storeCategorySale.key();
            StoreCategoryKey similarStoreCategoryKey = storeCategorySale.similarKey();
            Map<LocalDate, Sales> dailySales = plannedMonthLastYearDailySales.get(similarStoreCategoryKey);
            int days = dailySales.size();
            BigDecimal forecastTurnover = plannedMonth.turnover().multiply(BigDecimal.valueOf(days));
            BigDecimal forecastMargin = clampMargin(plannedMonth.margin().multiply(BigDecimal.valueOf(days)), forecastTurnover);
            Sales forecast = new Sales(forecastTurnover, forecastMargin);


            String storeName = storeCategoryKey.storeName();
            List<LocalDate> storeHolidays = holidays.get(storeName);
            if (storeHolidays == null) {
                throw new IllegalStateException("Cannot find holidays by store: " + storeName);
            }

            ForecastStoreCategorySales forecastStoreCategorySale = new ForecastStoreCategorySales(
                    storeCategoryKey,
                    similarStoreCategoryKey,
                    sameMonthLastYear,
                    plannedMonthLastYear,
                    currentMonth,
                    plannedMonth,
                    days,
                    forecast,
                    calculateDailyShares(dailySales, storeHolidays)
            );

            result.add(forecastStoreCategorySale);
        }

        return result;
    }

    static BigDecimal calculateTurnoverForecast(Sales sameMonthLastYear,
                                                Sales plannedMonthLastYear,
                                                Sales currentMonth,
                                                Function<Sales, BigDecimal> function) {
        BigDecimal sameMonthLastYearValue = function.apply(sameMonthLastYear);
        BigDecimal plannedMonthLastYearValue = function.apply(plannedMonthLastYear);
        BigDecimal currentMonthValue = function.apply(currentMonth);
        BigDecimal dynamic = plannedMonthLastYearValue.divide(sameMonthLastYearValue, PRECISE_MATH_CONTEXT);
        return currentMonthValue.multiply(dynamic);
    }

    static BigDecimal marginRateOf(Sales sales) {
        BigDecimal turnover = sales.turnover();
        if (turnover == null || turnover.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return sales.margin().divide(turnover, PRECISE_MATH_CONTEXT);
    }

    static BigDecimal calculateMarginForecast(Sales sameMonthLastYear,
                                               Sales plannedMonthLastYear,
                                               Sales currentMonth,
                                               BigDecimal plannedMonthTurnover) {
        BigDecimal currentMarginRate = marginRateOf(currentMonth);
        BigDecimal sameMarginRate = marginRateOf(sameMonthLastYear);
        BigDecimal plannedMarginRate = marginRateOf(plannedMonthLastYear);

        BigDecimal rateDynamic = sameMarginRate.signum() == 0
                ? BigDecimal.ONE
                : plannedMarginRate.divide(sameMarginRate, PRECISE_MATH_CONTEXT);

        BigDecimal forecastMarginRate = currentMarginRate.multiply(rateDynamic, PRECISE_MATH_CONTEXT);
        return plannedMonthTurnover.multiply(forecastMarginRate, PRECISE_MATH_CONTEXT);
    }

    private static BigDecimal clampMargin(BigDecimal margin, BigDecimal turnover) {
        return margin.min(turnover);
    }

    Map<LocalDate, Sales> calculateDailyShares(Map<LocalDate, Sales> dailySales, List<LocalDate> holidays) {
        YearMonth plannedYearMonth = YearMonth.of(yearMonthProperties.year(), yearMonthProperties.month());
        YearMonth previousYearPlannedYearMonth = plannedYearMonth.minusYears(1);

        Map<Boolean, List<LocalDate>> partitioned = holidays.stream()
                .filter(date -> {
                    YearMonth ym = YearMonth.from(date);
                    return ym.equals(plannedYearMonth) || ym.equals(previousYearPlannedYearMonth);
                })
                .collect(Collectors.partitioningBy(
                        date -> YearMonth.from(date).equals(plannedYearMonth)
                ));

        List<LocalDate> currentHolidays = partitioned.get(true);
        List<LocalDate> previousYearHolidays = partitioned.get(false);

        Map<LocalDate, Sales> adjustedDailySales = new HashMap<>();

        for (int i = 1; i <= plannedYearMonth.lengthOfMonth(); i++) {
            LocalDate plannedDate = LocalDate.of(plannedYearMonth.getYear(), plannedYearMonth.getMonthValue(), i);
            if (currentHolidays.contains(plannedDate)) {
                continue;
            }

            LocalDate previousDate = findCorrespondingPreviousYearDate(plannedDate);
            Sales sales = dailySales.get(previousDate);
            if (previousYearHolidays.contains(previousDate)) {
                for (int j = 1; j <= 7; j++) {
                    LocalDate candidateDate = previousDate.minusDays(j);
                    LocalDate shiftedDate = candidateDate.equals(candidateDate.with(firstDayOfMonth())) ? previousDate.plusDays(j) : candidateDate;
                    Sales shiftedSales = dailySales.get(shiftedDate);
                    if (nonNull(shiftedSales)) {
                        sales = shiftedSales;
                        break;
                    }
                }
            }

            if (nonNull(sales)) {
                adjustedDailySales.put(plannedDate, sales);
            }
        }

        Map<LocalDate, Sales> filteredPlannedDailySales = adjustedDailySales.entrySet().stream()
                .filter(entry -> !currentHolidays.contains(entry.getKey()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        Sales totals = filteredPlannedDailySales.values().stream()
                .reduce(Sales.zero(), Sales::add);

        TreeMap<LocalDate, Sales> shares = filteredPlannedDailySales.entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        entry -> {
                            Sales sales = entry.getValue();

                            BigDecimal turnoverShare = totals.turnover().signum() == 0
                                    ? BigDecimal.ZERO
                                    : sales.turnover()
                                    .divide(totals.turnover(), PRECISE_MATH_CONTEXT);

                            BigDecimal marginRate = sales.turnover().signum() == 0
                                    ? BigDecimal.ZERO
                                    : sales.margin()
                                    .divide(sales.turnover(), PRECISE_MATH_CONTEXT);

                            return new Sales(turnoverShare, marginRate);
                        },
                        (a, _) -> a,
                        TreeMap::new
                ));
        return finalizeShares(smoothShares(shares, 3));
    }

    private LocalDate findCorrespondingPreviousYearDate(LocalDate plannedDate) {
        DayOfWeek dayOfWeek = plannedDate.getDayOfWeek();
        int occurrence = (plannedDate.getDayOfMonth() - 1) / 7 + 1; // which occurrence: 1st, 2nd, 3rd...

        LocalDate prevYearMonthStart = LocalDate.of(
                plannedDate.getYear() - 1,
                plannedDate.getMonth(), 1
        );

        // Find the 1st occurrence of this weekday in prev year's month
        LocalDate firstOccurrence = prevYearMonthStart.with(TemporalAdjusters.nextOrSame(dayOfWeek));
        LocalDate candidate = firstOccurrence.plusWeeks(occurrence - 1);

        // Edge case: 5th weekday this year but only 4 exist last year → use 4th
        if (candidate.getMonth() != prevYearMonthStart.getMonth()) {
            candidate = firstOccurrence.plusWeeks(occurrence - 2);
        }

        return candidate;
    }

    static Map<LocalDate, Sales> smoothShares(Map<LocalDate, Sales> dailyShares, int windowHalfSize) {
        List<Map.Entry<LocalDate, Sales>> entries = new ArrayList<>(dailyShares.entrySet());
        Map<LocalDate, Sales> smoothed = new TreeMap<>();

        for (int i = 0; i < entries.size(); i++) {
            LocalDate date = entries.get(i).getKey();

            List<Sales> window = new ArrayList<>();
            for (int j = Math.max(0, i - windowHalfSize); j <= Math.min(entries.size() - 1, i + windowHalfSize); j++) {
                Sales s = entries.get(j).getValue();
                if (s.turnover().signum() != 0 || s.margin().signum() != 0) {
                    window.add(s);
                }
            }

            if (window.isEmpty()) {
                smoothed.put(date, entries.get(i).getValue());
            } else {
                BigDecimal count = BigDecimal.valueOf(window.size());

                BigDecimal avgTurnover = window.stream()
                        .map(Sales::turnover)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(count, PRECISE_MATH_CONTEXT);

                BigDecimal avgMargin = window.stream()
                        .map(Sales::margin)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(count, PRECISE_MATH_CONTEXT);

                smoothed.put(date, new Sales(avgTurnover, avgMargin));
            }
        }

        return smoothed;
    }

    static Map<LocalDate, Sales> finalizeShares(Map<LocalDate, Sales> smoothed) {
        BigDecimal totalTurnoverShare = smoothed.values().stream()
                .map(Sales::turnover)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<LocalDate, BigDecimal> turnoverShare = smoothed.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> totalTurnoverShare.signum() == 0
                                ? BigDecimal.ZERO
                                : entry.getValue().turnover().divide(totalTurnoverShare, PRECISE_MATH_CONTEXT)
                ));

        // raw margin share per day = that day's renormalized turnover share * that day's smoothed margin rate
        Map<LocalDate, BigDecimal> rawMarginShare = smoothed.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> turnoverShare.get(entry.getKey()).multiply(entry.getValue().margin(), PRECISE_MATH_CONTEXT)
                ));

        BigDecimal totalRawMarginShare = rawMarginShare.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return smoothed.keySet().stream()
                .collect(Collectors.toMap(
                        date -> date,
                        date -> new Sales(
                                turnoverShare.get(date),
                                totalRawMarginShare.signum() == 0
                                        ? BigDecimal.ZERO
                                        : rawMarginShare.get(date).divide(totalRawMarginShare, PRECISE_MATH_CONTEXT)
                        ),
                        (a, _) -> a,
                        TreeMap::new
                ));
    }
}
