package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.config.properties.CoreSheetRenewalProperties;
import com.etake.coresheetrenewal.model.AbcCategory;
import com.etake.coresheetrenewal.model.RegionOrder;
import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreCheckPlan;
import com.etake.coresheetrenewal.model.StoreChecks;
import com.etake.coresheetrenewal.model.StoreDailySales;
import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.model.StoreSalesPlan;
import com.etake.coresheetrenewal.model.StoreStock;
import com.etake.coresheetrenewal.repository.StorePerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorePerformanceService {
    private final StorePerformanceRepository repository;
    private final CoreSheetRenewalProperties properties;

    /** @return the actuals of every store that had sales in the month, by store name */
    public Map<String, StorePerformance> load(final YearMonth month) {
        final Map<String, List<StoreDailySales>> salesByStore = repository.getDailySales(month).stream()
                .collect(Collectors.groupingBy(StoreDailySales::storeName));
        final Map<String, StoreChecks> checks = repository.getSaleChecks(month).stream()
                .collect(Collectors.toMap(StoreChecks::storeName, Function.identity()));
        final Map<String, StoreSalesPlan> salesPlans = repository.getSalesPlans(month).stream()
                .collect(Collectors.toMap(StoreSalesPlan::storeName, Function.identity()));
        final Map<String, StoreCheckPlan> plans = repository.getCheckPlans(month).stream()
                .collect(Collectors.toMap(StoreCheckPlan::storeName, Function.identity()));
        final Map<String, Map<AbcCategory, BigDecimal>> stock = loadStock(month);
        log.info("Loaded {} for {} stores: {} check rows, {} sales plans, {} check plans, {} stock rows",
                month, salesByStore.size(), checks.size(), salesPlans.size(), plans.size(), stock.size());

        return salesByStore.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            final String store = entry.getKey();
            final List<StoreDailySales> sales = entry.getValue();
            final BigDecimal margin = sales.stream().map(StoreDailySales::margin).reduce(BigDecimal.ZERO, BigDecimal::add);
            final Map<LocalDate, BigDecimal> dailySales = sales.stream()
                    .collect(Collectors.toMap(StoreDailySales::date, StoreDailySales::sales));
            final MonthlyTotals totals = new MonthlyTotals(margin,
                    checks.getOrDefault(store, StoreChecks.empty(store)),
                    stock.getOrDefault(store, Map.of()));
            return new StorePerformance(store, dailySales, workedDaysPlan(salesPlans.get(store), dailySales), totals, plans.get(store));
        }));
    }

    /** @return the names of the stores with a plan for the month */
    public Set<String> loadPlannedStores(final YearMonth month) {
        final Set<String> planned = repository.getSalesPlans(month).stream()
                .map(StoreSalesPlan::storeName)
                .collect(Collectors.toSet());
        if (planned.isEmpty()) {
            log.warn("No plans for {} are loaded yet, so stores without sales on the last days will be treated as closed", month);
        }
        return planned;
    }

    /** @return the sort order of the regions that have one, by region name */
    public Map<String, Integer> loadRegionOrders() {
        return repository.getRegionOrders().stream()
                .collect(Collectors.toMap(RegionOrder::name, RegionOrder::sortOrder, Math::min));
    }

    /** @return the stores of the database by name */
    public Map<String, StoreLocation> loadLocations() {
        return repository.getStoreLocations().stream()
                .collect(Collectors.toMap(StoreLocation::storeName, Function.identity(), StoreLocation::latest));
    }

    private static BigDecimal workedDaysPlan(final StoreSalesPlan plan, final Map<LocalDate, BigDecimal> dailySales) {
        if (plan == null) {
            return null;
        }
        final int workedDays = StorePerformance.workedDays(dailySales);
        final BigDecimal turnover = plan.turnoverFor(workedDays);
        if (workedDays < plan.planDays()) {
            log.warn("Store «{}» worked {} of {} planned days, its plan is reduced from {} to {}",
                    plan.storeName(), workedDays, plan.planDays(), plan.turnover(), turnover);
        }
        return turnover;
    }

    private Map<String, Map<AbcCategory, BigDecimal>> loadStock(final YearMonth month) {
        if (!properties.abcStockEnabled()) {
            return Map.of();
        }
        return repository.getAbcStock(month).stream().collect(Collectors.groupingBy(StoreStock::storeName,
                Collectors.toMap(StoreStock::abcCategory, StoreStock::amount)));
    }
}
