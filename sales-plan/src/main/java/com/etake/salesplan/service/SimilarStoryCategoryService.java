package com.etake.salesplan.service;

import com.etake.salesplan.config.YearMonthProperties;
import com.etake.salesplan.model.*;
import com.etake.salesplan.model.enumeration.Period;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;
import static com.etake.salesplan.model.enumeration.Period.PLANNED_MONTH_LAST_YEAR;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarStoryCategoryService {
    private final YearMonthProperties yearMonthProperties;
    private final AverageSalesService averageSalesService;
    private final HolidaysService holidaysService;

    private record Candidate(StoreCategoryAveragedSales item, BigDecimal dynamic) {
    }

    public List<SimilarStoreCategoryAveragedSales> getSimilarStoreCategorySales(List<StoreCategorySales> sales) {
        int plannedYear = yearMonthProperties.year();
        int previousYear = plannedYear - 1;
        int plannedMonth = yearMonthProperties.month();
        YearMonth previousYearMonth = YearMonth.of(previousYear, plannedMonth);
        Map<String, Long> requiredDays = holidaysService.getHolidays().entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        entry -> {
                            long holidays = entry.getValue().stream()
                                    .filter(date -> YearMonth.from(date).equals(previousYearMonth))
                                    .count();
                            return previousYearMonth.lengthOfMonth() - holidays;
                        }
                ));
        List<String> lflStoresDuringMonth = sales.stream()
                .collect(groupingBy(
                        sale -> sale.key().storeId(),
                        collectingAndThen(
                                toList(),
                                storeSales -> {
                                    String store = storeSales.getFirst().key().storeName();
                                    return storeSales.stream()
                                            .map(storeCategorySales -> storeCategorySales.sales().get(PLANNED_MONTH_LAST_YEAR))
                                            .filter(Objects::nonNull)
                                            .flatMap(periodSales -> periodSales.keySet().stream())
                                            .distinct()
                                            .count() == requiredDays.getOrDefault(store, 0L);
                                })
                ))
                .entrySet().stream()
                .filter(entry -> entry.getValue() || entry.getKey().equals("013"))
                .map(Map.Entry::getKey)
                .toList();
        List<StoreCategoryAveragedSales> averagedSales = averageSalesService.getAveragedSales(sales);

        return averagedSales.stream()
                .map(s -> getSimilarStoreCategorySale(s, averagedSales, lflStoresDuringMonth))
                .toList();
    }

    private SimilarStoreCategoryAveragedSales getSimilarStoreCategorySale(StoreCategoryAveragedSales averagedSale,
                                                                          List<StoreCategoryAveragedSales> averagedSales,
                                                                          List<String> lflStoresDuringMonth) {
        boolean isLfl = isLfl(averagedSale, lflStoresDuringMonth, true);
        if (isLfl) {
            return SimilarStoreCategoryAveragedSales.same(averagedSale);
        }

        StoreCategoryKey key = averagedSale.key();
        StoreCategoryAveragedSales similarStoreCategorySales = getSimilarSales(key, averagedSales, lflStoresDuringMonth);
        Map<Period, Sales> currentSales = averagedSale.averagedSales();
        Map<Period, Sales> similarSales = new HashMap<>(similarStoreCategorySales.averagedSales());
        similarSales.put(Period.CURRENT_MONTH, currentSales.get(Period.CURRENT_MONTH));
        return new SimilarStoreCategoryAveragedSales(
                key,
                similarStoreCategorySales.key(),
                similarSales,
                false
        );
    }

    private StoreCategoryAveragedSales getSimilarSales(StoreCategoryKey key, List<StoreCategoryAveragedSales> averagedSales, List<String> lflStoresDuringMonth) {
        StoreCategoryAveragedSales targetStoreCategoryAveragedSales = averagedSales.stream()
                .filter(s -> s.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find key " + key));
        BigDecimal targetDynamic = dynamicOf(targetStoreCategoryAveragedSales);

        List<StoreCategoryAveragedSales> baseFilteredCombinations = averagedSales.stream()
                .filter(sale -> isLfl(sale, lflStoresDuringMonth))
                .filter(s -> !s.key().equals(key))
                .filter(s -> s.key().categoryId().equals(key.categoryId()))
                .toList();
        List<StoreCategoryAveragedSales> filteredByRegionCombinations = baseFilteredCombinations.stream()
                .filter(s -> s.key().regionId().equals(key.regionId()))
                .toList();
        List<StoreCategoryAveragedSales> filteredCombinations = filteredByRegionCombinations.isEmpty() ? baseFilteredCombinations : filteredByRegionCombinations;

        return filteredCombinations.stream()
                .map(s -> new Candidate(s, dynamicOf(s)))
                .min(comparing(c -> c.dynamic.subtract(targetDynamic).abs()))
                .map(Candidate::item)
                .orElseThrow(() -> new IllegalStateException("Cannot find best match"));
    }

    private static boolean isLfl(StoreCategoryAveragedSales averagedStoreCategorySales, List<String> lflStoresDuringMonth) {
        return isLfl(averagedStoreCategorySales, lflStoresDuringMonth, false);
    }

    private static boolean isLfl(StoreCategoryAveragedSales averagedStoreCategorySales, List<String> lflStoresDuringMonth, boolean logWarning) {
        StoreCategoryKey key = averagedStoreCategorySales.key();
        Map<Period, Map<LocalDate, Sales>> dailySalesByPeriod = averagedStoreCategorySales.dailySales();
        boolean lflStoreDuringPlannedMonthLastYear = lflStoresDuringMonth.contains(key.storeId());
        boolean lflByStoreCategory = Stream.of(
                Period.TWO_MONTHS_AGO_LAST_YEAR,
                Period.PREVIOUS_MONTH_LAST_YEAR,
                Period.SAME_MONTH_LAST_YEAR,
                PLANNED_MONTH_LAST_YEAR
        ).allMatch(period -> hasSales(dailySalesByPeriod.get(period)));

        if (!lflStoreDuringPlannedMonthLastYear && lflByStoreCategory && logWarning) {
            log.warn("Store '{}' (category '{}') is LFL, but was not operational for the full month last year",
                    key.storeName(), key.categoryName());
        }

        return lflStoreDuringPlannedMonthLastYear && lflByStoreCategory;
    }

    private static boolean hasSales(Map<LocalDate, Sales> sales) {
        return sales.values().stream()
                .map(Sales::turnover)
                .filter(Objects::nonNull)
                .anyMatch(turnover -> turnover.signum() > 0);
    }

    private static BigDecimal dynamicOf(StoreCategoryAveragedSales s) {
        Sales cur = s.averagedSales().get(Period.CURRENT_MONTH);
        Sales prev = s.averagedSales().get(Period.PREVIOUS_MONTH);
        if (cur == null || prev == null) {
            throw new IllegalStateException("Cannot calculate dynamic coefficient for " + s.key());
        }
        BigDecimal curTurnover = cur.turnover();
        BigDecimal prevTurnover = prev.turnover();
        if (prevTurnover == null || prevTurnover.signum() == 0) {
            return BigDecimal.ONE;
        }

        return curTurnover.divide(prevTurnover, PRECISE_MATH_CONTEXT);
    }
}
