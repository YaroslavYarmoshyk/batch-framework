package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.model.AbcCategory;
import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.service.MonthlyTotalsService;
import com.etake.googlesheets.SheetCells;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * The cells to write into the sheet of a finished month. Only the cells with data are written, so the formulas of the
 * sheet stay intact: the plans and daily sales of summary rows are formulas, the facts of summary rows are values.
 */
@RequiredArgsConstructor
public final class ClosedMonthValues {
    private final String sheetTitle;
    private final YearMonth month;
    private final CoreSheetLayout layout;
    private final Map<String, StorePerformance> performance;
    private final MonthlyTotalsService totalsService;

    public List<ValueRange> build() {
        final List<ValueRange> values = new ArrayList<>();
        for (final CoreSheetRow row : layout.rows()) {
            switch (row.kind()) {
                case STORE -> {
                    final StorePerformance store = performance.get(row.name());
                    if (store != null) {
                        addStore(values, row, store);
                    }
                }
                case REGION, COMPANY -> addTotals(values, row, sumOfStores(row));
            }
        }
        return values;
    }

    private void addStore(final List<ValueRange> values, final CoreSheetRow row, final StorePerformance store) {
        addTotals(values, row, store.totals());
        addCell(values, row, CoreSheetColumn.SALES_PLAN, store.salesPlan());
        addCell(values, row, CoreSheetColumn.DAYS, store.workedDays());
        if (store.plan() != null) {
            addCell(values, row, CoreSheetColumn.AVG_CHECK_PLAN, store.plan().avgCheck());
            addCell(values, row, CoreSheetColumn.ITEMS_PER_CHECK_PLAN, store.plan().avgItemsPerCheck());
        }
        values.add(range(row, CoreSheetColumn.FIRST_DAY, IntStream.rangeClosed(1, month.lengthOfMonth())
                .mapToObj(day -> SheetCells.toCellValue(store.dailySales().get(month.atDay(day))))
                .toList()));
    }

    private void addTotals(final List<ValueRange> values, final CoreSheetRow row, final MonthlyTotals totals) {
        addCell(values, row, CoreSheetColumn.MARGIN, totals.margin());
        addCell(values, row, CoreSheetColumn.AVG_CHECK_FACT, totals.checks().avgCheck());
        addCell(values, row, CoreSheetColumn.LOYALTY_AVG_CHECK_FACT, totals.checks().loyaltyAvgCheck());
        addCell(values, row, CoreSheetColumn.ITEMS_PER_CHECK_FACT, totals.checks().avgItemsPerCheck());
        if (!totals.stock().isEmpty()) {
            values.add(range(row, CoreSheetColumn.STOCK_A, Arrays.stream(AbcCategory.values())
                    .map(category -> SheetCells.toCellValue(totals.stock().get(category)))
                    .toList()));
        }
    }

    private MonthlyTotals sumOfStores(final CoreSheetRow summary) {
        return totalsService.sum(summary.name(), layout.storesOf(summary).stream()
                .map(store -> performance.get(store.name()))
                .filter(Objects::nonNull)
                .map(StorePerformance::totals)
                .toList());
    }

    private void addCell(final List<ValueRange> values, final CoreSheetRow row, final CoreSheetColumn column, final Object value) {
        if (value != null) {
            values.add(range(row, column, List.of(SheetCells.toCellValue(value))));
        }
    }

    private ValueRange range(final CoreSheetRow row, final CoreSheetColumn firstColumn, final List<Object> cells) {
        return new ValueRange()
                .setRange(SheetCells.a1(sheetTitle, firstColumn.getLetter() + row.number()))
                .setValues(List.of(cells));
    }
}
