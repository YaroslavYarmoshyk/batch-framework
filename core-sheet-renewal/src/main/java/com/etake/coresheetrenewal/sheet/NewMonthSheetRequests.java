package com.etake.coresheetrenewal.sheet;

import com.etake.googlesheets.SheetCells;
import com.etake.googlesheets.SheetRequests;
import com.google.api.services.sheets.v4.model.ColorStyle;
import com.google.api.services.sheets.v4.model.Request;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static com.etake.coresheetrenewal.sheet.CoreSheetLayout.FIRST_DATA_ROW;
import static com.etake.googlesheets.SheetRequests.clearValues;
import static com.etake.googlesheets.SheetRequests.fillNumber;
import static com.etake.googlesheets.SheetRequests.range;

/**
 * The requests that turn the sheet of a finished month into the sheet of the next one. They are sent as one atomic
 * batch, so the spreadsheet never ends up with a half-prepared month.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NewMonthSheetRequests {
    /** The tab colour of the month in progress. */
    private static final ColorStyle CURRENT_MONTH_TAB = SheetRequests.rgb(0x38, 0x76, 0x1D);
    /** Cell A1 holds the first day of the month; the headers of the sheet are formulas derived from it. */
    private static final int MONTH_CELL_ROW = 0;
    private static final int MONTH_CELL_COLUMN = 0;
    private static final List<CoreSheetColumn> FACT_COLUMNS = List.of(
            CoreSheetColumn.AVG_CHECK_FACT, CoreSheetColumn.LOYALTY_AVG_CHECK_FACT, CoreSheetColumn.ITEMS_PER_CHECK_FACT,
            CoreSheetColumn.MARGIN);
    private static final List<CoreSheetColumn> STORE_INPUT_COLUMNS = List.of(
            CoreSheetColumn.SALES_PLAN, CoreSheetColumn.AVG_CHECK_PLAN, CoreSheetColumn.ITEMS_PER_CHECK_PLAN);

    /**
     * @param structureRequests the changes of the new sheet's rows, which come right after it is created
     * @param layout            the rows of the new sheet after those changes
     */
    public static List<Request> build(final int sourceSheetId,
                                      final int sourceIndex,
                                      final int newSheetId,
                                      final String newTitle,
                                      final YearMonth newMonth,
                                      final List<Request> structureRequests,
                                      final CoreSheetLayout layout) {
        final List<Request> requests = new ArrayList<>();
        requests.add(SheetRequests.duplicateSheet(sourceSheetId, newSheetId, newTitle, sourceIndex));
        requests.add(SheetRequests.clearTabColor(sourceSheetId));
        requests.add(SheetRequests.tabColor(newSheetId, CURRENT_MONTH_TAB));
        requests.add(fillNumber(range(newSheetId, MONTH_CELL_ROW, 1, MONTH_CELL_COLUMN, 1),
                SheetCells.toSerialNumber(newMonth.atDay(1))));
        requests.addAll(structureRequests);

        // Facts are plain values on every row, including the region and company ones.
        final int firstRow = FIRST_DATA_ROW - 1;
        final int rowCount = layout.lastRowNumber() - firstRow;
        FACT_COLUMNS.forEach(column -> requests.add(clearValues(
                range(newSheetId, firstRow, rowCount, column.index(), 1))));
        requests.add(clearValues(range(newSheetId, firstRow, rowCount,
                CoreSheetColumn.STOCK_A.index(), CoreSheetColumn.STOCK_D.index() - CoreSheetColumn.STOCK_A.index() + 1)));

        // On region and company rows these columns are formulas, so they are reset store by store.
        final int dayColumns = CoreSheetColumn.LAST_DAY.index() - CoreSheetColumn.FIRST_DAY.index() + 1;
        for (final CoreSheetRow store : layout.stores()) {
            final int row = store.number() - 1;
            STORE_INPUT_COLUMNS.forEach(column -> requests.add(clearValues(range(newSheetId, row, 1, column.index(), 1))));
            requests.add(clearValues(range(newSheetId, row, 1, CoreSheetColumn.FIRST_DAY.index(), dayColumns)));
            requests.add(fillNumber(range(newSheetId, row, 1, CoreSheetColumn.DAYS.index(), 1), newMonth.lengthOfMonth()));
        }
        return requests;
    }
}
