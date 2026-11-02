package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.config.properties.CoreSheetRenewalProperties;
import com.etake.coresheetrenewal.model.StoreDailySales;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.model.StoreSalesPlan;
import com.etake.coresheetrenewal.repository.StorePerformanceRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

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
        return service.load(OCTOBER).get("Магазин");
    }

    @Test
    void aStoreClosedMidMonthGetsThePlanOfItsWorkedDays() {
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
    void aStoreOpenedMidMonthKeepsThePlanOfItsPlannedDays() {
        final StorePerformance store = loadStoreWith("200000.55", 2, "100", "200");

        assertThat(store.salesPlan()).isEqualByComparingTo("200000.55");
    }

    @Test
    void aStoreWithoutAPlanStaysWithoutOne() {
        assertThat(loadStoreWith(null, 0, "100").salesPlan()).isNull();
    }
}
