package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.config.properties.CoreSheetRenewalProperties;
import com.etake.coresheetrenewal.model.AbcCategory;
import com.etake.coresheetrenewal.model.RegionOrder;
import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreCheckPlan;
import com.etake.coresheetrenewal.model.StoreChecks;
import com.etake.coresheetrenewal.model.StoreDailySales;
import com.etake.coresheetrenewal.model.StoreFirstSale;
import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.model.StoreSalesPlan;
import com.etake.coresheetrenewal.model.StoreStock;
import com.etake.coresheetrenewal.repository.StorePerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private static final BigDecimal CLOSING_PLAN_EXECUTION = BigDecimal.ONE;
    private static final BigDecimal OPENING_PLAN_EXECUTION = new BigDecimal("0.95");
    private static final int MONEY_SCALE = 2;
    private static final int ITEMS_SCALE = 4;

    private final StorePerformanceRepository repository;
    private final CoreSheetRenewalProperties properties;

    /**
     * @param locations          the stores of the database by name
     * @param plannedForNewMonth the stores with a plan for the month after the given one
     * @return the actuals of every store that had sales in the month, by store name
     */
    public Map<String, StorePerformance> load(final YearMonth month,
                                              final Map<String, StoreLocation> locations,
                                              final Set<String> plannedForNewMonth) {
        final Map<String, List<StoreDailySales>> salesByStore = repository.getDailySales(month).stream()
                .collect(Collectors.groupingBy(StoreDailySales::storeName));
        final Map<String, StoreChecks> checks = repository.getSaleChecks(month).stream()
                .collect(Collectors.toMap(StoreChecks::storeName, Function.identity()));
        final Map<String, StoreSalesPlan> salesPlans = repository.getSalesPlans(month).stream()
                .collect(Collectors.toMap(StoreSalesPlan::storeName, Function.identity()));
        final Map<String, StoreCheckPlan> plans = repository.getCheckPlans(month).stream()
                .collect(Collectors.toMap(StoreCheckPlan::storeName, Function.identity()));
        final Map<String, LocalDate> firstSales = repository.getFirstSales(month).stream()
                .collect(Collectors.toMap(StoreFirstSale::storeName, StoreFirstSale::firstSaleDate));
        final Map<String, Map<AbcCategory, BigDecimal>> stock = loadStock(month);
        log.info("Loaded {} for {} stores: {} check rows, {} sales plans, {} check plans, {} stock rows",
                month, salesByStore.size(), checks.size(), salesPlans.size(), plans.size(), stock.size());

        final LocalDate monthStart = month.atDay(1);
        final LocalDate newMonthStart = month.plusMonths(1).atDay(1);
        return salesByStore.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            final String store = entry.getKey();
            final List<StoreDailySales> sales = entry.getValue();
            final BigDecimal margin = sales.stream().map(StoreDailySales::margin).reduce(BigDecimal.ZERO, BigDecimal::add);
            final Map<LocalDate, BigDecimal> dailySales = sales.stream()
                    .collect(Collectors.toMap(StoreDailySales::date, StoreDailySales::sales));
            final StoreChecks storeChecks = checks.getOrDefault(store, StoreChecks.empty(store));
            final MonthlyTotals totals = new MonthlyTotals(margin, storeChecks, stock.getOrDefault(store, Map.of()));

            final BigDecimal execution = planExecution(store, dailySales, locations.get(store), firstSales.get(store),
                    monthStart, newMonthStart, plannedForNewMonth.contains(store));
            if (execution == null) {
                return new StorePerformance(store, dailySales, workedDaysPlan(salesPlans.get(store), dailySales), totals, plans.get(store));
            }
            final BigDecimal fact = dailySales.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            final StoreCheckPlan checkPlan = new StoreCheckPlan(store,
                    planOf(storeChecks.avgCheck(), execution, MONEY_SCALE),
                    planOf(storeChecks.avgItemsPerCheck(), execution, ITEMS_SCALE));
            return new StorePerformance(store, dailySales, planOf(fact, execution, MONEY_SCALE), totals, checkPlan);
        }));
    }

    /**
     * The execution a store's plan is set to from its actuals: a closing store meets its plan exactly, an opening one
     * nearly. A store that both opened and closed within the month counts as closing.
     *
     * @return {@code null} for a store working as usual, which keeps its plan from the database
     */
    private static BigDecimal planExecution(final String store, final Map<LocalDate, BigDecimal> dailySales,
                                            final StoreLocation location, final LocalDate firstSale,
                                            final LocalDate monthStart, final LocalDate newMonthStart,
                                            final boolean plannedForNewMonth) {
        if (StoreClosureRule.isClosed(location, dailySales, newMonthStart, plannedForNewMonth)) {
            log.warn("Store «{}» is closing, its plans are set to {} of its actuals", store, CLOSING_PLAN_EXECUTION);
            return CLOSING_PLAN_EXECUTION;
        }
        if (firstSale != null && !firstSale.isBefore(monthStart)) {
            log.warn("Store «{}» opened on {}, its plans are set for {} execution of its actuals", store, firstSale, OPENING_PLAN_EXECUTION);
            return OPENING_PLAN_EXECUTION;
        }
        return null;
    }

    private static BigDecimal planOf(final BigDecimal fact, final BigDecimal execution, final int scale) {
        return fact == null ? null : fact.divide(execution, scale, RoundingMode.HALF_UP);
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
