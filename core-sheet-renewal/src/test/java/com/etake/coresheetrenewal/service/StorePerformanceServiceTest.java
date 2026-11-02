package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.config.properties.CoreSheetRenewalProperties;
import com.etake.coresheetrenewal.model.StoreCheckPlan;
import com.etake.coresheetrenewal.model.StoreChecks;
import com.etake.coresheetrenewal.model.StoreDailySales;
import com.etake.coresheetrenewal.model.StoreFirstSale;
import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.model.StoreSalesPlan;
import com.etake.coresheetrenewal.repository.StorePerformanceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorePerformanceServiceTest {
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    private final StorePerformanceRepository repository = mock(StorePerformanceRepository.class);
    private final StorePerformanceService service = new StorePerformanceService(repository,
            new CoreSheetRenewalProperties(2026, 11, false, null));

    private StorePerformance loadStoreWith(final String plan, final int planDays, final String... salesByDay) {
        final List<StoreDailySales> sales = new ArrayList<>();
        for (int day = 0; day < salesByDay.length; day++) {
            sales.add(new StoreDailySales("Магазин", OCTOBER.atDay(day + 1), new BigDecimal(salesByDay[day]), BigDecimal.ONE));
        }
        when(repository.getDailySales(OCTOBER)).thenReturn(sales);
        when(repository.getSalesPlans(OCTOBER)).thenReturn(plan == null ? List.of()
                : List.of(new StoreSalesPlan("Магазин", new BigDecimal(plan), planDays)));
        return service.load(OCTOBER, Map.of(), Set.of()).get("Магазин");
    }

    @Test
    void aStoreMissingDaysGetsThePlanOfItsWorkedDays() {
        final StorePerformance store = loadStoreWith("3100000", 31, "100", "200");

        assertThat(store.workedDays()).isEqualTo(2);
        assertThat(store.salesPlan()).isEqualByComparingTo("200000");
    }

    @Test
    void aDayOfOnlyRefundsIsNotAWorkingDay() {
        final StorePerformance store = loadStoreWith("3100000", 31, "100", "-50", "0");

        assertThat(store.workedDays()).isEqualTo(1);
        assertThat(store.salesPlan()).isEqualByComparingTo("100000");
    }

    @Test
    void aStoreWorkingAllPlannedDaysKeepsItsPlan() {
        final StorePerformance store = loadStoreWith("200000.55", 2, "100", "200");

        assertThat(store.salesPlan()).isEqualByComparingTo("200000.55");
    }

    @Test
    void aStoreWithoutAPlanStaysWithoutOne() {
        assertThat(loadStoreWith(null, 0, "100").salesPlan()).isNull();
    }

    /** Sells on the 30th and 31st, 2 checks of 95 with 3 positions each, with a plan unrelated to the actuals. */
    private void storeSellingToTheEndOfTheMonth() {
        when(repository.getDailySales(OCTOBER)).thenReturn(List.of(
                new StoreDailySales("Магазин", OCTOBER.atDay(30), new BigDecimal("570"), BigDecimal.ONE),
                new StoreDailySales("Магазин", OCTOBER.atDay(31), new BigDecimal("380"), BigDecimal.ONE)));
        when(repository.getSaleChecks(OCTOBER)).thenReturn(List.of(new StoreChecks(
                "Магазин", 2, new BigDecimal("190"), 6, 0, BigDecimal.ZERO)));
        when(repository.getSalesPlans(OCTOBER)).thenReturn(List.of(new StoreSalesPlan("Магазин", new BigDecimal("9999"), 31)));
        when(repository.getCheckPlans(OCTOBER)).thenReturn(List.of(new StoreCheckPlan("Магазин", new BigDecimal("999"), new BigDecimal("9"))));
    }

    private static Map<String, StoreLocation> locatedWith(final LocalDate closeDate) {
        return Map.of("Магазин", new StoreLocation("Магазин", "Регіон", closeDate));
    }

    @Test
    void aClosingStoreMeetsAllItsPlansExactly() {
        storeSellingToTheEndOfTheMonth();

        final StorePerformance store = service.load(OCTOBER, locatedWith(OCTOBER.atDay(31)), Set.of()).get("Магазин");

        assertThat(store.salesPlan()).isEqualByComparingTo("950");
        assertThat(store.plan().avgCheck()).isEqualByComparingTo("95");
        assertThat(store.plan().avgItemsPerCheck()).isEqualByComparingTo("3");
    }

    @Test
    void aStoreWithoutSalesOnTheLastDaysAndWithoutANewPlanIsClosing() {
        when(repository.getDailySales(OCTOBER)).thenReturn(List.of(
                new StoreDailySales("Магазин", OCTOBER.atDay(10), new BigDecimal("570"), BigDecimal.ONE)));

        assertThat(service.load(OCTOBER, locatedWith(null), Set.of()).get("Магазин").salesPlan()).isEqualByComparingTo("570");
    }

    @Test
    void anOpeningStoreIsAt95PercentOfItsPlans() {
        storeSellingToTheEndOfTheMonth();
        when(repository.getFirstSales(OCTOBER)).thenReturn(List.of(new StoreFirstSale("Магазин", OCTOBER.atDay(30))));

        final StorePerformance store = service.load(OCTOBER, locatedWith(null), Set.of()).get("Магазин");

        assertThat(store.salesPlan()).isEqualByComparingTo("1000");
        assertThat(store.plan().avgCheck()).isEqualByComparingTo("100");
        assertThat(store.plan().avgItemsPerCheck()).isEqualByComparingTo("3.1579");
    }

    @Test
    void aStoreOpenedAndClosedWithinTheMonthCountsAsClosing() {
        storeSellingToTheEndOfTheMonth();
        when(repository.getFirstSales(OCTOBER)).thenReturn(List.of(new StoreFirstSale("Магазин", OCTOBER.atDay(30))));

        assertThat(service.load(OCTOBER, locatedWith(OCTOBER.atDay(31)), Set.of()).get("Магазин").salesPlan())
                .isEqualByComparingTo("950");
    }

    @Test
    void aStoreWorkingAsUsualKeepsItsPlansFromTheDatabase() {
        storeSellingToTheEndOfTheMonth();
        when(repository.getFirstSales(OCTOBER)).thenReturn(List.of(new StoreFirstSale("Магазин", OCTOBER.minusMonths(2).atDay(1))));

        final StorePerformance store = service.load(OCTOBER, locatedWith(null), Set.of()).get("Магазин");

        assertThat(store.plan().avgCheck()).isEqualByComparingTo("999");
        assertThat(store.salesPlan()).isLessThan(new BigDecimal("9999"));
    }
}
