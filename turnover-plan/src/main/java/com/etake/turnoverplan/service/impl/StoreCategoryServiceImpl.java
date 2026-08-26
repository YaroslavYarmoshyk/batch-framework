package com.etake.turnoverplan.service.impl;

import com.etake.turnoverplan.config.properties.SystemConfigurationProperties;
import com.etake.turnoverplan.model.Position;
import com.etake.turnoverplan.model.StoreCategory;
import com.etake.turnoverplan.model.StoreCategorySales;
import com.etake.turnoverplan.repository.PositionRepository;
import com.etake.turnoverplan.repository.StoreCategoryRepository;
import com.etake.turnoverplan.service.PeriodProvider;
import com.etake.turnoverplan.service.StoreCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.etake.turnoverplan.service.PeriodProvider.getYearMonth;
import static com.etake.turnoverplan.utils.CalculationUtils.DEFAULT_ROUNDING_MODE;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
@RequiredArgsConstructor
public class StoreCategoryServiceImpl implements StoreCategoryService {
    private final SystemConfigurationProperties systemConfigurationProperties;
    private final StoreCategoryRepository storeCategoryRepository;
    private final PositionRepository positionRepository;
    private final PeriodProvider periodProvider;

    @Override
    public List<StoreCategorySales> getSales() {
        final YearMonth currentPeriodCurrentMonth = periodProvider.getCurrentYearMonth();
        final YearMonth currentPeriodPrevMonth = getYearMonth(currentPeriodCurrentMonth, 0, -1);
        final YearMonth prevPeriodPrevMonth = getYearMonth(currentPeriodCurrentMonth, -1, -1);
        final YearMonth prevPeriodCurrentMonth = getYearMonth(currentPeriodCurrentMonth, -1, 0);
        final YearMonth prevPeriodFirstMonth = getYearMonth(currentPeriodCurrentMonth, -1, 1);
        final YearMonth prevPeriodSecondMonth = getYearMonth(currentPeriodCurrentMonth, -1, 2);
        final YearMonth prevPeriodThirdMonth = getYearMonth(currentPeriodCurrentMonth, -1, 3);

        final Map<String, Position> currentPeriodPrevMonthPositions = getPositionsByKey(currentPeriodPrevMonth);
        final Map<String, Position> currentPeriodCurrentMonthPositions = getPositionsByKey(currentPeriodCurrentMonth);
        final Map<String, Position> prevPeriodCurrentMonthPositions = getPositionsByKey(prevPeriodCurrentMonth);
        final Map<String, Position> prevPeriodFirstMonthPositions = getPositionsByKey(prevPeriodFirstMonth);
        final Map<String, Position> prevPeriodSecondMonthPositions = getPositionsByKey(prevPeriodSecondMonth);
        final Map<String, Position> prevPeriodThirdMonthPositions = getPositionsByKey(prevPeriodThirdMonth);

        final Set<String> lflStores = getPositionsByKey(prevPeriodPrevMonth).values().stream()
                .map(Position::storeName)
                .collect(toSet());

        final List<StoreCategory> activeStoreCategories = storeCategoryRepository.getActiveStoreCategoryPairs();
        final Map<String, String> similarStoreCategories = getSimilarStoreCategories(
                activeStoreCategories,
                lflStores,
                currentPeriodPrevMonthPositions,
                currentPeriodCurrentMonthPositions
        );

        return activeStoreCategories.stream()
                .map(storeCategory -> {
                    final String regionName = storeCategory.regionName();
                    final String storeName = storeCategory.storeName();
                    final String key = storeCategory.getKey();
                    final boolean isLfl = lflStores.contains(storeName);
                    final String similarStoreName = isLfl ? storeName : similarStoreCategories.get(storeCategory.getKey());

                    return StoreCategorySales.builder()
                            .regionName(regionName)
                            .storeName(storeName)
                            .categoryName(storeCategory.categoryName())

                            .currentPeriodCurrentMonthAvgTurnover(getValueByKey(key, currentPeriodCurrentMonthPositions, Position::turnover))
                            .prevPeriodCurrentMonthAvgTurnover(getValueByKey(key, prevPeriodCurrentMonthPositions, Position::turnover))
                            .prevPeriodFirstMonthAvgTurnover(getValueByKey(key, prevPeriodFirstMonthPositions, Position::turnover))
                            .prevPeriodSecondMonthAvgTurnover(getValueByKey(key, prevPeriodSecondMonthPositions, Position::turnover))
                            .prevPeriodThirdMonthAvgTurnover(getValueByKey(key, prevPeriodThirdMonthPositions, Position::turnover))

                            .currentPeriodCurrentMonthAvgMargin(getValueByKey(key, currentPeriodCurrentMonthPositions, Position::margin))
                            .prevPeriodCurrentMonthAvgMargin(getValueByKey(key, prevPeriodCurrentMonthPositions, Position::margin))
                            .prevPeriodFirstMonthAvgMargin(getValueByKey(key, prevPeriodFirstMonthPositions, Position::margin))
                            .prevPeriodSecondMonthAvgMargin(getValueByKey(key, prevPeriodSecondMonthPositions, Position::margin))
                            .prevPeriodThirdMonthAvgMargin(getValueByKey(key, prevPeriodThirdMonthPositions, Position::margin))

                            .prevPeriodFirstMonthSalesDays(getValueByKey(key, prevPeriodFirstMonthPositions, Position::salesDays))
                            .prevPeriodSecondMonthSalesDays(getValueByKey(key, prevPeriodSecondMonthPositions, Position::salesDays))
                            .prevPeriodThirdMonthSalesDays(getValueByKey(key, prevPeriodThirdMonthPositions, Position::salesDays))

                            .isLfl(isLfl)

                            .similarStore(similarStoreName)

                            .build();
                })
                .sorted(Comparator.comparing(StoreCategorySales::getRegionName).thenComparing(StoreCategorySales::getStoreName))
                .toList();
    }

    private static Map<String, String> getSimilarStoreCategories(final List<StoreCategory> activeStoreCategories,
                                                                 final Set<String> lflStores,
                                                                 final Map<String, Position> currentPeriodPrevMonthPositions,
                                                                 final Map<String, Position> currentPeriodCurrentMonthPositions) {
//        Collect by region and category to get only stores from current region for growth comparison
        final Map<String, Map<String, Map<String, BigDecimal>>> growthPerRegionCategory = currentPeriodCurrentMonthPositions.values().stream()
                .collect(groupingBy(
                        Position::regionName,
                        groupingBy(
                                Position::categoryName,
                                toMap(
                                        Position::getKey,
                                        entry -> getGrowth(entry.turnover(), currentPeriodPrevMonthPositions.getOrDefault(entry.getKey(), Position.empty()).turnover())
                                )
                        )
                ));
        return activeStoreCategories.stream()
                .collect(toMap(
                        StoreCategory::getKey,
                        storeCategory -> getClosestStoreByGrowth(
                                storeCategory,
                                lflStores,
                                growthPerRegionCategory.get(storeCategory.regionName()).getOrDefault(storeCategory.categoryName(), Map.of())
                        )
                ));
    }

    private static BigDecimal getGrowth(final BigDecimal currentMonthAvgTurnover,
                                        final BigDecimal prevMonthAvgTurnover) {
        if (currentMonthAvgTurnover != null && prevMonthAvgTurnover != null) {
            return currentMonthAvgTurnover
                    .divide(prevMonthAvgTurnover, DEFAULT_ROUNDING_MODE).subtract(BigDecimal.ONE);
        }
        return BigDecimal.ZERO;
    }

    private static String getClosestStoreByGrowth(final StoreCategory storeCategory,
                                                  final Set<String> lflStores,
                                                  final Map<String, BigDecimal> growth) {
        final String currentStore = storeCategory.storeName();
        final String currentStoreCategoryKey = storeCategory.getKey();
        final BigDecimal currentStoreCategoryGrowth = growth.get(currentStoreCategoryKey);
        if (isNull(currentStoreCategoryGrowth)) {
            return currentStore;
        }
        final Map<String, BigDecimal> growthExcludingCurrentStoreCategory = growth.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), currentStoreCategoryKey))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        return growthExcludingCurrentStoreCategory.entrySet().stream()
                .filter(entry -> nonNull(entry.getValue()))
                .filter(entry -> lflStores.contains(resolveStoreNameFromStoreCategoryKey(entry.getKey())))// Filter out entries with null values
                .min((entry1, entry2) -> {
                    BigDecimal diff1 = entry1.getValue().subtract(currentStoreCategoryGrowth).abs();
                    BigDecimal diff2 = entry2.getValue().subtract(currentStoreCategoryGrowth).abs();
                    return diff1.compareTo(diff2);  // Compare the absolute differences
                })
                .map(entry -> resolveStoreNameFromStoreCategoryKey(entry.getKey()))
                .orElse(currentStore);
    }

    private static String resolveStoreNameFromStoreCategoryKey(final String key) {
        return Optional.ofNullable(key)
                .map(k -> k.substring(0, k.indexOf("_0")))
                .orElseThrow();
    }

    private <T> T getValueByKey(final String key,
                                final Map<String, Position> positions,
                                final Function<Position, T> function) {
        final Position position = positions.getOrDefault(key, Position.empty());
        return function.apply(position);
    }

    private Map<String, Position> getPositionsByKey(final YearMonth yearMonth) {
        final LocalDate fromDate = yearMonth.atDay(1);
        final LocalDate toDay = yearMonth.atEndOfMonth();
        final List<Position> positions = positionRepository.findAllInPeriod(fromDate, toDay, systemConfigurationProperties.exclusionDates());
        return positions.stream()
                .collect(toMap(
                        Position::getKey,
                        Function.identity()
                ));
    }
}
