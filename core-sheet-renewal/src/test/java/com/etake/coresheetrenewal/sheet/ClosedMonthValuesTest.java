package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.model.AbcCategory;
import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreCheckPlan;
import com.etake.coresheetrenewal.model.StoreChecks;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.service.MonthlyTotalsService;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ClosedMonthValuesTest {
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final CoreSheetLayout LAYOUT = CoreSheetLayout.parse(
            List.of("Регіон", "Магазин 1", "Магазин 2", "Без продажів", "=\"Разом\""),
            List.of("=SUM(AI4:AI6)", "", "", "", "=SUM(AI3)"));

    private static StorePerformance store(final String name, final long checks, final String amount,
                                          final long positions, final long loyaltyChecks, final String loyaltyAmount,
                                          final StoreCheckPlan plan, final Map<AbcCategory, BigDecimal> stock) {
        final StoreChecks storeChecks = new StoreChecks(name, checks, new BigDecimal(amount), positions, loyaltyChecks, new BigDecimal(loyaltyAmount));
        return new StorePerformance(name, Map.of(OCTOBER.atDay(1), new BigDecimal("100.5"), OCTOBER.atDay(31), new BigDecimal("200")),
                plan == null ? null : new BigDecimal("1000000.5"),
                new MonthlyTotals(new BigDecimal("50"), storeChecks, stock), plan);
    }

    private static Map<String, List<Object>> build(final Map<String, StorePerformance> performance) {
        return new ClosedMonthValues("Жовтень 26", OCTOBER, LAYOUT, performance, new MonthlyTotalsService()).build().stream()
                .collect(Collectors.toMap(ValueRange::getRange, range -> range.getValues().getFirst()));
    }

    private static final Map<String, StorePerformance> PERFORMANCE = Map.of(
            "Магазин 1", store("Магазин 1", 100, "30000", 300, 50, "20000", new StoreCheckPlan("Магазин 1", new BigDecimal("250"), new BigDecimal("2.5")), Map.of()),
            "Магазин 2", store("Магазин 2", 300, "30000", 300, 150, "20000", null, Map.of()));

    @Test
    void storeRowsGetFactsPlansAndDailySales() {
        final Map<String, List<Object>> values = build(PERFORMANCE);

        assertThat(values.get("'Жовтень 26'!L4")).containsExactly(300.0);
        assertThat(values.get("'Жовтень 26'!O4")).containsExactly(400.0);
        assertThat(values.get("'Жовтень 26'!R4")).containsExactly(3.0);
        assertThat(values.get("'Жовтень 26'!AF4")).containsExactly(50.0);
        assertThat(values.get("'Жовтень 26'!C4")).containsExactly(1000000.5);
        assertThat(values.get("'Жовтень 26'!BO4")).containsExactly(2);
        assertThat(values.get("'Жовтень 26'!K4")).containsExactly(250.0);
        assertThat(values.get("'Жовтень 26'!Q4")).containsExactly(2.5);

        final List<Object> days = values.get("'Жовтень 26'!AI4");
        assertThat(days).hasSize(31);
        assertThat(days.getFirst()).isEqualTo(100.5);
        assertThat(days.get(1)).isEqualTo("");
        assertThat(days.getLast()).isEqualTo(200.0);
    }

    @Test
    void regionAveragesAreWeightedByChecksNotAveragedOverStores() {
        final Map<String, List<Object>> values = build(PERFORMANCE);

        // 60 000 over 400 checks, whereas the mean of the two store averages (300 and 100) would be 200.
        assertThat(values.get("'Жовтень 26'!L3")).containsExactly(150.0);
        assertThat(values.get("'Жовтень 26'!O3")).containsExactly(200.0);
        assertThat(values.get("'Жовтень 26'!R3")).containsExactly(1.5);
        assertThat(values.get("'Жовтень 26'!AF3")).containsExactly(100.0);
        assertThat(values.get("'Жовтень 26'!L7")).containsExactly(150.0);
    }

    @Test
    void theFormulasOfSummaryRowsStayIntact() {
        final Map<String, List<Object>> values = build(PERFORMANCE);

        assertThat(values).doesNotContainKeys(
                "'Жовтень 26'!BO3", "'Жовтень 26'!C3", "'Жовтень 26'!K3", "'Жовтень 26'!Q3", "'Жовтень 26'!AI3",
                "'Жовтень 26'!BO7", "'Жовтень 26'!C7", "'Жовтень 26'!K7", "'Жовтень 26'!Q7", "'Жовтень 26'!AI7");
    }

    @Test
    void aStoreWithoutAPlanOrSalesKeepsItsCells() {
        final Map<String, List<Object>> values = build(PERFORMANCE);

        assertThat(values).doesNotContainKeys("'Жовтень 26'!C5", "'Жовтень 26'!K5", "'Жовтень 26'!Q5");
        assertThat(values.keySet()).noneMatch(range -> range.matches(".*![A-Z]+6"));
    }

    @Test
    void stockIsWrittenOnlyWhenLoadedAndAddsUpOnSummaryRows() {
        assertThat(build(PERFORMANCE)).doesNotContainKeys("'Жовтень 26'!Z4", "'Жовтень 26'!Z3");

        final Map<AbcCategory, BigDecimal> stock = Map.of(AbcCategory.A, BigDecimal.TEN, AbcCategory.D, BigDecimal.ONE);
        final Map<String, List<Object>> values = build(Map.of(
                "Магазин 1", store("Магазин 1", 1, "1", 1, 0, "0", null, stock),
                "Магазин 2", store("Магазин 2", 1, "1", 1, 0, "0", null, stock)));

        assertThat(values.get("'Жовтень 26'!Z4")).containsExactly(10.0, "", "", 1.0);
        assertThat(values.get("'Жовтень 26'!Z3")).containsExactly(20.0, "", "", 2.0);
    }
}
