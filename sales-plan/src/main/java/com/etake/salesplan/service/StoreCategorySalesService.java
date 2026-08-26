package com.etake.salesplan.service;

import com.etake.salesplan.config.YearMonthProperties;
import com.etake.salesplan.model.*;
import com.etake.salesplan.model.enumeration.Period;
import com.etake.salesplan.repository.StoreCategorySalesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.etake.salesplan.model.enumeration.Period.*;
import static com.etake.salesplan.utils.stream.SalesCollectors.byKeyThenDay;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreCategorySalesService {
    private final YearMonthProperties yearMonthProperties;
    private final StoreCategorySalesRepository storeCategorySalesRepository;
    private final SimilarStoryCategoryService similarStoryCategoryService;
    private final ForecastService forecastService;
    private final DistributionService distributionService;
    private final NewStoreForecastService newStoreForecastService;
    private final DailyStoreCategoryPlanService dailyStoreCategoryPlanService;
    private final ExcelService excelService;
    private final PlanCatalogService planCatalogService;
    @Value("${system-configurations.distribution.enabled}")
    private boolean enabledDistribution;
    @Value("${system-configurations.upload-plans}")
    private boolean uploadPlans;
    @Value("${system-configurations.create-report}")
    private boolean createReport;

    public void getSales() throws IOException {
        YearMonth planned = YearMonth.of(yearMonthProperties.year(), yearMonthProperties.month());
        YearMonth current = planned.minusMonths(1);
        YearMonth previous = current.minusMonths(1);
        YearMonth lastYearPlanned = planned.minusYears(1);
        YearMonth lastYearCurrent = current.minusYears(1);
        YearMonth lastYearPrevious = lastYearCurrent.minusMonths(1);
        YearMonth lastYearTwoMonthsAgo = lastYearPrevious.minusMonths(1);

        List<NewStoreCandidate> newStoreCandidates = storeCategorySalesRepository.getNewStoreCandidates(current);
        Set<String> newStoreIds = newStoreCandidates.stream().map(NewStoreCandidate::storeId).collect(Collectors.toSet());

        // Only stores/categories present in the plans have a target to forecast against; anything
        // else (e.g. a store or category not yet added to 'Stores Plan.xlsx'/'Categories Plan.xlsx')
        // is excluded up front instead of being computed and then dropped downstream.
        Set<String> planStores = planCatalogService.getStoreTargets().keySet();
        Set<String> planCategories = planCatalogService.getCategoryTurnoverTargets().keySet();

        var currentRecords = storeCategorySalesRepository.getSalesRecords(current).stream().collect(byKeyThenDay(current.getYear(), current.getMonthValue()));
        var previousRecords = storeCategorySalesRepository.getSalesRecords(previous).stream().collect(byKeyThenDay(previous.getYear(), previous.getMonthValue()));
        Map<StoreCategoryKey, Map<LocalDate, Sales>> lastYearPlannedRecords = storeCategorySalesRepository.getSalesRecords(lastYearPlanned).stream().collect(byKeyThenDay(lastYearPlanned.getYear(), lastYearPlanned.getMonthValue()));
        var lastYearCurrentRecords = storeCategorySalesRepository.getSalesRecords(lastYearCurrent).stream().collect(byKeyThenDay(lastYearCurrent.getYear(), lastYearCurrent.getMonthValue()));
        var lastYearPreviousRecords = storeCategorySalesRepository.getSalesRecords(lastYearPrevious).stream().collect(byKeyThenDay(lastYearPrevious.getYear(), lastYearPrevious.getMonthValue()));
        var lastYearTwoMonthsAgoRecords = storeCategorySalesRepository.getSalesRecords(lastYearTwoMonthsAgo).stream().collect(byKeyThenDay(lastYearTwoMonthsAgo.getYear(), lastYearTwoMonthsAgo.getMonthValue()));

        Map<LocalDate, Sales> empty = Map.of();
        List<StoreCategoryKey> currentKeys = currentRecords.keySet().stream().toList();
        List<String> offPlanStores = currentKeys.stream()
                .map(StoreCategoryKey::storeName)
                .filter(storeName -> !planStores.contains(storeName))
                .distinct()
                .toList();
        List<String> offPlanCategories = currentKeys.stream()
                .map(StoreCategoryKey::categoryName)
                .filter(categoryName -> !planCategories.contains(categoryName))
                .distinct()
                .toList();
        if (!offPlanStores.isEmpty()) {
            log.warn("Stores present in the sales data but absent from 'Stores Plan.xlsx' are excluded from forecasting: {}", offPlanStores);
        }
        if (!offPlanCategories.isEmpty()) {
            log.warn("Categories present in the sales data but absent from 'Categories Plan.xlsx' are excluded from forecasting: {}", offPlanCategories);
        }

        List<StoreCategorySales> sales = currentRecords.entrySet().stream()
                .map(entry -> {
                    StoreCategoryKey key = entry.getKey();
                    Map<Period, Map<LocalDate, Sales>> sale = Map.of(
                            CURRENT_MONTH, entry.getValue(),
                            PREVIOUS_MONTH, previousRecords.getOrDefault(key, empty),
                            PLANNED_MONTH_LAST_YEAR, lastYearPlannedRecords.getOrDefault(key, empty),
                            SAME_MONTH_LAST_YEAR, lastYearCurrentRecords.getOrDefault(key, empty),
                            PREVIOUS_MONTH_LAST_YEAR, lastYearPreviousRecords.getOrDefault(key, empty),
                            TWO_MONTHS_AGO_LAST_YEAR, lastYearTwoMonthsAgoRecords.getOrDefault(key, empty)
                    );
                    return new StoreCategorySales(
                            key,
                            sale
                    );
                })
                .filter(storeCategorySales -> !newStoreIds.contains(storeCategorySales.key().storeId()))
                .filter(storeCategorySales -> planStores.contains(storeCategorySales.key().storeName()))
                .filter(storeCategorySales -> planCategories.contains(storeCategorySales.key().categoryName()))
                .toList();
        List<SimilarStoreCategoryAveragedSales> similarStoreCategorySales = similarStoryCategoryService.getSimilarStoreCategorySales(sales);

        Map<StoreCategoryKey, Map<LocalDate, Sales>> plannedMonthLastYearDailySales = sales.stream()
                .collect(toMap(
                        StoreCategorySales::key,
                        storeCategorySales -> storeCategorySales.sales().get(Period.PLANNED_MONTH_LAST_YEAR)
                ));
        List<ForecastStoreCategorySales> forecastSales = forecastService.getForecastStoreCategorySales(similarStoreCategorySales, plannedMonthLastYearDailySales);
        List<ForecastStoreCategorySales> newStoreForecastSales = newStoreForecastService.getNewStoreForecastSales(newStoreCandidates, planned);
        List<ForecastStoreCategorySales> mergedForecastSalse = Stream.concat(forecastSales.stream(), newStoreForecastSales.stream()).toList();

        List<ForecastStoreCategorySales> finalForecastSales = enabledDistribution ? distributionService.getDistributedStoreCategorySales(mergedForecastSalse) : mergedForecastSalse;

        List<DailySales> dailySales = finalForecastSales.stream()
                .map(StoreCategorySalesService::mapToDailySales)
                .toList();

        if (uploadPlans) {
            dailyStoreCategoryPlanService.uploadPlans(dailySales);
        }

        if (createReport) {
            excelService.generateReport(dailySales);
        }
    }

    private static DailySales mapToDailySales(ForecastStoreCategorySales forecastStoreCategorySales) {
        Sales forecast = forecastStoreCategorySales.forecast();
        BigDecimal turnover = forecast.turnover();
        BigDecimal margin = forecast.margin();
        Map<LocalDate, Sales> dailySales = forecastStoreCategorySales.dailyShares().entrySet().stream()
                .collect(
                        toMap(
                                Map.Entry::getKey,
                                entry -> {
                                    Sales sales = entry.getValue();
                                    BigDecimal turnoverShare = sales.turnover();
                                    BigDecimal marginShare = sales.margin();
                                    return new Sales(turnover.multiply(turnoverShare), margin.multiply(marginShare));
                                },
                                (a, _) -> a,
                                TreeMap::new
                        )
                );
        StoreCategoryKey storeCategoryKey = forecastStoreCategorySales.key();
        return new DailySales(
                storeCategoryKey.regionId(),
                storeCategoryKey.storeId(),
                storeCategoryKey.storeName(),
                forecastStoreCategorySales.similarKey().storeName(),
                forecastStoreCategorySales.key().categoryId(),
                forecastStoreCategorySales.key().categoryName(),
                dailySales
        );
    }

    public static boolean isWithinRange(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
