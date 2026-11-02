package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.sheet.ClosedMonthValues;
import com.etake.coresheetrenewal.sheet.CoreSheetColumn;
import com.etake.coresheetrenewal.sheet.CoreSheetLayout;
import com.etake.coresheetrenewal.sheet.CoreSheetRow;
import com.etake.coresheetrenewal.sheet.CoreSheetTitles;
import com.etake.coresheetrenewal.sheet.NewMonthSheetRequests;
import com.etake.coresheetrenewal.sheet.SheetStructureEditor;
import com.etake.coresheetrenewal.sheet.StoreStructureSync;
import com.etake.coresheetrenewal.sheet.SummaryRowTemplates;
import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.googlesheets.SheetCells;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Fills the finished month's sheet with actuals and creates the new month's sheet from it, with the rows in line with
 * the database. Safe to re-run: the actuals are overwritten, and a sheet that already exists is left alone, so data
 * entered there is never lost. The only sheet whose cells are ever read is the finished month's one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreSheetRenewalService {
    private final GoogleSheetsGateway gateway;
    private final MonthlyTotalsService totalsService;

    public void renew(final YearMonth newMonth,
                      final Map<String, StorePerformance> performance,
                      final Map<String, StoreLocation> locations,
                      final Set<String> plannedStores,
                      final Map<String, Integer> regionOrders) {
        final YearMonth closedMonth = newMonth.minusMonths(1);
        final String closedTitle = CoreSheetTitles.of(closedMonth);
        final String newTitle = CoreSheetTitles.of(newMonth);

        final List<SheetProperties> sheets = gateway.getSpreadsheet("sheets.properties(sheetId,title,index)").getSheets().stream()
                .map(Sheet::getProperties)
                .toList();
        final SheetProperties closedSheet = sheets.stream()
                .filter(sheet -> closedTitle.equals(sheet.getTitle()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Sheet «%s» not found in the spreadsheet".formatted(closedTitle)));

        StoreStructureSync.requireLastDaysLoaded(newMonth.atDay(1), performance);

        final CoreSheetLayout sheetLayout = readLayout(closedTitle);
        final SummaryRowTemplates templates = readTemplates(closedTitle, sheetLayout);

        final SheetStructureEditor closedEditor = new SheetStructureEditor(closedSheet.getSheetId(), sheetLayout, templates, regionOrders);
        StoreStructureSync.addMissingStores(closedEditor, sheetLayout, performance.keySet(), locations, newMonth.atDay(1));
        if (closedEditor.hasChanges()) {
            gateway.batchUpdate(closedEditor.finish());
        }
        final CoreSheetLayout closedLayout = closedEditor.layout();
        warnAboutRowsWithoutSales(closedTitle, closedLayout, performance);

        gateway.writeValues(new ClosedMonthValues(closedTitle, closedMonth, closedLayout, performance, totalsService).build());
        log.info("Filled sheet «{}» with the actuals of {} stores", closedTitle, performance.size());

        if (sheets.stream().anyMatch(sheet -> newTitle.equals(sheet.getTitle()))) {
            log.info("Sheet «{}» already exists, leaving it as is", newTitle);
            return;
        }
        final int newSheetId = newSheetId(sheets);
        final SheetStructureEditor newEditor = new SheetStructureEditor(newSheetId, closedLayout, templates, regionOrders);
        StoreStructureSync.applyToNewMonth(newEditor, closedLayout, newMonth.atDay(1), locations, performance, plannedStores);
        gateway.batchUpdate(NewMonthSheetRequests.build(closedSheet.getSheetId(), closedSheet.getIndex(),
                newSheetId, newTitle, newMonth, newEditor.finish(), newEditor.layout()));
        log.info("Created sheet «{}» from «{}»", newTitle, closedTitle);
    }

    private CoreSheetLayout readLayout(final String title) {
        final int firstRow = CoreSheetLayout.FIRST_DATA_ROW;
        final String firstDay = CoreSheetColumn.FIRST_DAY.getLetter();
        final List<ValueRange> columns = gateway.readFormulas(List.of(
                SheetCells.a1(title, "A%d:A".formatted(firstRow)),
                SheetCells.a1(title, "%s%d:%s".formatted(firstDay, firstRow, firstDay))));
        return CoreSheetLayout.parse(toTexts(columns.getFirst()), toTexts(columns.getLast()));
    }

    private SummaryRowTemplates readTemplates(final String title, final CoreSheetLayout layout) {
        final List<ValueRange> rows = gateway.readFormulas(List.of(
                templateRange(title, firstRowOf(layout, CoreSheetRow.Kind.STORE)),
                templateRange(title, firstRowOf(layout, CoreSheetRow.Kind.REGION)),
                templateRange(title, firstRowOf(layout, CoreSheetRow.Kind.COMPANY))));
        return new SummaryRowTemplates(rowCells(rows.get(0)), rowCells(rows.get(1)), rowCells(rows.get(2)));
    }

    private static String templateRange(final String title, final int row) {
        return SheetCells.a1(title, "%d:%d".formatted(row, row));
    }

    private static int firstRowOf(final CoreSheetLayout layout, final CoreSheetRow.Kind kind) {
        return layout.rows().stream()
                .filter(row -> row.kind() == kind)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The sheet has no %s row".formatted(kind)))
                .number();
    }

    private static List<String> rowCells(final ValueRange row) {
        return row.getValues().getFirst().stream().map(Object::toString).toList();
    }

    /** The API omits trailing blank cells, so a row without a value comes back as an empty list. */
    private static List<String> toTexts(final ValueRange column) {
        return Objects.requireNonNullElse(column.getValues(), List.<List<Object>>of()).stream()
                .map(row -> row.isEmpty() ? "" : row.getFirst().toString())
                .toList();
    }

    private static void warnAboutRowsWithoutSales(final String title,
                                                  final CoreSheetLayout layout,
                                                  final Map<String, StorePerformance> performance) {
        final List<String> withoutSales = layout.stores().stream()
                .map(CoreSheetRow::name)
                .filter(store -> !performance.containsKey(store))
                .toList();
        if (!withoutSales.isEmpty()) {
            log.warn("Rows of sheet «{}» without sales in the database, left unfilled: {}", title, withoutSales);
        }
    }

    private static int newSheetId(final List<SheetProperties> sheets) {
        final Set<Integer> used = sheets.stream().map(SheetProperties::getSheetId).collect(Collectors.toSet());
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        } while (!used.add(id));
        return id;
    }
}
