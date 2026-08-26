package com.etake.salesplan.service;

import com.etake.salesplan.config.DistributionProperties;
import com.etake.salesplan.model.ForecastStoreCategorySales;
import com.etake.salesplan.model.Sales;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;
import static com.etake.salesplan.utils.WeightUtils.normalizeWeights;
import static java.util.function.Function.identity;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionService {
    private final PlanCatalogService planCatalogService;
    private final DistributionProperties distributionProperties;

    public List<ForecastStoreCategorySales> getDistributedStoreCategorySales(List<ForecastStoreCategorySales> storeCategorySales) {
        Map<String, BigDecimal> storeTarget = planCatalogService.getStoreTargets();
        Map<String, BigDecimal> categoryTargetTurnover = planCatalogService.getCategoryTurnoverTargets();
        Map<String, BigDecimal> categoryTargetMargin = planCatalogService.getCategoryMarginTargets();

        Map<CellKey, BigDecimal> x = new HashMap<>();
        for (ForecastStoreCategorySales i : storeCategorySales) {
            BigDecimal v = i.forecast().turnover();
            if (v.signum() < 0) {
                v = BigDecimal.ZERO;
            }
            x.put(new CellKey(i.key().storeName(), i.key().categoryName()), v);
        }

        Map<String, BigDecimal> categoryWeights = normalizeWeights(categoryTargetTurnover);
        Map<String, BigDecimal> storeWeights = normalizeWeights(storeTarget);

        for (int iter = 0; iter < distributionProperties.maxIterations(); iter++) {
            // 1) Scale rows (stores)
            for (String store : storeTarget.keySet()) {
                BigDecimal target = storeTarget.get(store);
                if (target.signum() < 0) {
                    target = BigDecimal.ZERO;
                }

                BigDecimal rowSum = sumRow(x, store);
                if (rowSum.signum() == 0) {
                    if (target.signum() == 0) {
                        continue;
                    }
                    for (String cat : categoryTargetTurnover.keySet()) {
                        x.put(new CellKey(store, cat), target.multiply(categoryWeights.get(cat), PRECISE_MATH_CONTEXT));
                    }
                } else {
                    BigDecimal factor = target.divide(rowSum, PRECISE_MATH_CONTEXT);
                    scaleRow(x, store, factor);
                }
            }

            // 2) Scale columns (categories)
            for (String cat : categoryTargetTurnover.keySet()) {
                BigDecimal target = categoryTargetTurnover.get(cat);
                if (target.signum() < 0) target = BigDecimal.ZERO;

                BigDecimal colSum = sumCol(x, cat);
                if (colSum.signum() == 0) {
                    if (target.signum() == 0) {
                        continue;
                    }
                    // distribute target across stores proportionally to store targets
                    for (String store : storeTarget.keySet()) {
                        x.put(new CellKey(store, cat), target.multiply(storeWeights.get(store), PRECISE_MATH_CONTEXT));
                    }
                } else {
                    BigDecimal factor = target.divide(colSum, PRECISE_MATH_CONTEXT);
                    scaleCol(x, cat, factor);
                }
            }

            // 3) Convergence check: max relative error across all store + category totals
            BigDecimal maxErr = BigDecimal.ZERO;

            for (String store : storeTarget.keySet()) {
                BigDecimal target = storeTarget.get(store);
                BigDecimal actual = sumRow(x, store);
                maxErr = max(maxErr, relErr(actual, target));
            }
            for (String cat : categoryTargetTurnover.keySet()) {
                BigDecimal target = categoryTargetTurnover.get(cat);
                BigDecimal actual = sumCol(x, cat);
                maxErr = max(maxErr, relErr(actual, target));
            }

            if (maxErr.compareTo(distributionProperties.epsilon()) <= 0) {
                break;
            }
        }

        // Build updated list
        Map<CellKey, ForecastStoreCategorySales> byCell = storeCategorySales.stream()
                .collect(Collectors.toMap(
                        i -> new CellKey(i.key().storeName(), i.key().categoryName()),
                        identity()
                ));

        List<ForecastStoreCategorySales> updated = new ArrayList<>(storeCategorySales.size());

        for (ForecastStoreCategorySales i : storeCategorySales) {
            CellKey cell = new CellKey(i.key().storeName(), i.key().categoryName());
            if (!byCell.containsKey(cell)) {
                updated.add(i); // unchanged (not in both plans)
                continue;
            }

            BigDecimal newTurnover = x.get(cell);

            // Margin = turnover * category margin rate (from plan)
            BigDecimal catT = categoryTargetTurnover.get(cell.categoryName);
            BigDecimal catM = categoryTargetMargin.get(cell.categoryName);
            BigDecimal rate = (catT.signum() == 0) ? BigDecimal.ZERO : catM.divide(catT, PRECISE_MATH_CONTEXT);
            BigDecimal newMargin = newTurnover.multiply(rate, PRECISE_MATH_CONTEXT);

            Sales newForecast = new Sales(newTurnover, newMargin);

            updated.add(new ForecastStoreCategorySales(
                    i.key(),
                    i.similarKey(),
                    i.sameMonthLastYear(),
                    i.plannedMonthLasYear(),
                    i.currentMonth(),
                    i.plannedMonth(),
                    i.days(),
                    newForecast,
                    i.dailyShares()
            ));
        }

        Set<String> forecastedStores = storeCategorySales.stream()
                .map(i -> i.key().storeName())
                .collect(Collectors.toSet());
        List<String> targetedButNotForecasted = storeTarget.keySet().stream()
                .filter(store -> !forecastedStores.contains(store))
                .toList();
        if (!targetedButNotForecasted.isEmpty()) {
            log.warn("Stores present in 'Stores Plan.xlsx' but absent from the forecast input, so no plan was emitted for them: {}", targetedButNotForecasted);
        }

        log.info("Forecast distributed according to provided plans");

        return updated;
    }

    private static BigDecimal sumRow(Map<CellKey, BigDecimal> x, String store) {
        BigDecimal s = BigDecimal.ZERO;
        for (Map.Entry<CellKey, BigDecimal> e : x.entrySet()) {
            if (e.getKey().storeName.equals(store)) s = s.add(e.getValue(), PRECISE_MATH_CONTEXT);
        }
        return s;
    }

    private static BigDecimal sumCol(Map<CellKey, BigDecimal> x, String category) {
        BigDecimal s = BigDecimal.ZERO;
        for (Map.Entry<CellKey, BigDecimal> e : x.entrySet()) {
            if (e.getKey().categoryName.equals(category)) s = s.add(e.getValue(), PRECISE_MATH_CONTEXT);
        }
        return s;
    }

    private static void scaleRow(Map<CellKey, BigDecimal> x, String store, BigDecimal factor) {
        for (Map.Entry<CellKey, BigDecimal> e : x.entrySet()) {
            if (e.getKey().storeName.equals(store)) {
                e.setValue(e.getValue().multiply(factor, PRECISE_MATH_CONTEXT));
            }
        }
    }

    private static void scaleCol(Map<CellKey, BigDecimal> x, String category, BigDecimal factor) {
        for (Map.Entry<CellKey, BigDecimal> e : x.entrySet()) {
            if (e.getKey().categoryName.equals(category)) {
                e.setValue(e.getValue().multiply(factor, PRECISE_MATH_CONTEXT));
            }
        }
    }

    private static BigDecimal relErr(BigDecimal actual, BigDecimal target) {
        if (target.signum() == 0) {
            return actual.signum() == 0 ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        return actual.subtract(target, PRECISE_MATH_CONTEXT).abs().divide(target.abs(), PRECISE_MATH_CONTEXT);
    }

    private static BigDecimal max(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private record CellKey(String storeName, String categoryName) {}
}
