package com.etake.salesplan.service;

import com.etake.salesplan.config.NewStoreProperties;
import com.etake.salesplan.model.CategoryCatalogEntry;
import com.etake.salesplan.model.ForecastStoreCategorySales;
import com.etake.salesplan.model.NewStoreCandidate;
import com.etake.salesplan.model.Sales;
import com.etake.salesplan.model.StoreCategoryKey;
import com.etake.salesplan.repository.StoreCategorySalesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewStoreForecastService {
    private final StoreCategorySalesRepository storeCategorySalesRepository;
    private final PlanCatalogService planCatalogService;
    private final HolidaysService holidaysService;
    private final NewStoreProperties newStoreProperties;

    public List<ForecastStoreCategorySales> getNewStoreForecastSales(List<NewStoreCandidate> candidates, YearMonth planned) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, BigDecimal> storeTargets = planCatalogService.getStoreTargets();
        Map<String, BigDecimal> categoryWeights = planCatalogService.getCategoryTurnoverWeights();
        Map<String, BigDecimal> categoryMarginRates = planCatalogService.getCategoryMarginRates();
        Map<String, String> categoryIdsByName = storeCategorySalesRepository.getCategoryCatalog().stream()
                .collect(toMap(CategoryCatalogEntry::categoryName, CategoryCatalogEntry::categoryId, (a, _) -> a));
        Map<String, List<LocalDate>> holidays = holidaysService.getHolidays();

        int tradingDataBased = 0;
        int manualOverride = 0;
        int skipped = 0;

        List<ForecastStoreCategorySales> result = new ArrayList<>();

        for (NewStoreCandidate candidate : candidates) {
            List<LocalDate> storeHolidays = holidays.getOrDefault(candidate.storeName(), List.of());
            Map<LocalDate, Sales> dailyShares = buildFlatDailyShares(planned, storeHolidays);
            int plannedTradingDays = dailyShares.size();

            BigDecimal planTurnover;
            if (candidate.tradingDays() != null && candidate.tradingDays() > 0) {
                BigDecimal dailyAvgTurnover = candidate.turnover().divide(BigDecimal.valueOf(candidate.tradingDays()), PRECISE_MATH_CONTEXT);
                BigDecimal baseline = dailyAvgTurnover.multiply(BigDecimal.valueOf(plannedTradingDays), PRECISE_MATH_CONTEXT);
                planTurnover = baseline.divide(newStoreProperties.achievementTarget(), PRECISE_MATH_CONTEXT);
                tradingDataBased++;
            } else {
                BigDecimal manualTarget = storeTargets.get(candidate.storeName());
                if (manualTarget == null) {
                    log.warn("New store '{}' (opened {}) has no trading history and no entry in 'Stores Plan.xlsx' — skipping, no plan can be generated",
                            candidate.storeName(), candidate.openDate());
                    skipped++;
                    continue;
                }
                planTurnover = manualTarget;
                manualOverride++;
            }

            for (Map.Entry<String, BigDecimal> categoryWeight : categoryWeights.entrySet()) {
                String categoryName = categoryWeight.getKey();
                String categoryId = categoryIdsByName.get(categoryName);
                if (categoryId == null) {
                    log.warn("Category '{}' from 'Categories Plan.xlsx' has no matching category id — skipped for new store '{}'",
                            categoryName, candidate.storeName());
                    continue;
                }

                BigDecimal categoryTurnover = planTurnover.multiply(categoryWeight.getValue(), PRECISE_MATH_CONTEXT);
                BigDecimal marginRate = categoryMarginRates.getOrDefault(categoryName, BigDecimal.ZERO);
                BigDecimal categoryMargin = categoryTurnover.multiply(marginRate, PRECISE_MATH_CONTEXT);

                StoreCategoryKey key = new StoreCategoryKey(candidate.regionId(), candidate.storeId(), categoryId, candidate.storeName(), categoryName);
                Sales forecast = new Sales(categoryTurnover, categoryMargin);

                result.add(new ForecastStoreCategorySales(
                        key,
                        key,
                        Sales.zero(),
                        Sales.zero(),
                        Sales.zero(),
                        Sales.zero(),
                        plannedTradingDays,
                        forecast,
                        dailyShares
                ));
            }
        }

        log.info("New-store plans generated: {} trading-data-based, {} manual overrides, {} skipped (no data)",
                tradingDataBased, manualOverride, skipped);

        return result;
    }

    private static Map<LocalDate, Sales> buildFlatDailyShares(YearMonth planned, List<LocalDate> storeHolidays) {
        List<LocalDate> tradingDates = new ArrayList<>();
        for (int day = 1; day <= planned.lengthOfMonth(); day++) {
            LocalDate date = LocalDate.of(planned.getYear(), planned.getMonthValue(), day);
            if (!storeHolidays.contains(date)) {
                tradingDates.add(date);
            }
        }
        if (tradingDates.isEmpty()) {
            for (int day = 1; day <= planned.lengthOfMonth(); day++) {
                tradingDates.add(LocalDate.of(planned.getYear(), planned.getMonthValue(), day));
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
