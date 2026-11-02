package com.etake.coresheetrenewal.sheet;

import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class NewMonthSheetRequestsTest {
    private static final int SOURCE_ID = 67552306;
    private static final int NEW_ID = 777;
    private static final CoreSheetLayout LAYOUT = CoreSheetLayout.parse(
            List.of("Регіон", "Магазин", "=\"Разом\""),
            List.of("=SUM(AI4:AI4)", "", "=SUM(AI3)"));
    private static final List<Request> REQUESTS = NewMonthSheetRequests.build(
            SOURCE_ID, 1, NEW_ID, "Листопад 26", YearMonth.of(2026, 11), List.of(), LAYOUT);

    private static List<RepeatCellRequest> repeats() {
        return REQUESTS.stream().map(Request::getRepeatCell).filter(Objects::nonNull).toList();
    }

    /** Whether some request resets the cell, which is 0-based. */
    private static boolean cleared(final int row, final int column) {
        return repeats().stream()
                .filter(repeat -> repeat.getCell().getUserEnteredValue() == null)
                .map(RepeatCellRequest::getRange)
                .anyMatch(range -> covers(range, row, column));
    }

    private static boolean covers(final GridRange range, final int row, final int column) {
        return range.getStartRowIndex() <= row && row < range.getEndRowIndex()
                && range.getStartColumnIndex() <= column && column < range.getEndColumnIndex();
    }

    @Test
    void duplicatesTheSourceSoTheNewMonthComesFirst() {
        final var duplicate = REQUESTS.getFirst().getDuplicateSheet();

        assertThat(duplicate.getSourceSheetId()).isEqualTo(SOURCE_ID);
        assertThat(duplicate.getNewSheetId()).isEqualTo(NEW_ID);
        assertThat(duplicate.getNewSheetName()).isEqualTo("Листопад 26");
        assertThat(duplicate.getInsertSheetIndex()).isEqualTo(1);
    }

    @Test
    void movesTheGreenTabToTheNewMonth() {
        final var source = REQUESTS.get(1).getUpdateSheetProperties();
        assertThat(source.getProperties().getSheetId()).isEqualTo(SOURCE_ID);
        assertThat(source.getProperties().getTabColorStyle()).isNull();
        assertThat(source.getFields()).isEqualTo("tabColorStyle");

        final var target = REQUESTS.get(2).getUpdateSheetProperties();
        assertThat(target.getProperties().getSheetId()).isEqualTo(NEW_ID);
        assertThat(target.getProperties().getTabColorStyle().getRgbColor().getGreen()).isEqualTo(118 / 255f);
    }

    @Test
    void setsTheMonthDateAndTheNumberOfDays() {
        assertThat(repeats()).anySatisfy(repeat -> {
            assertThat(covers(repeat.getRange(), 0, 0)).isTrue();
            assertThat(repeat.getCell().getUserEnteredValue().getNumberValue()).isEqualTo(46327d);
        });
        assertThat(repeats()).anySatisfy(repeat -> {
            assertThat(covers(repeat.getRange(), 3, CoreSheetColumn.DAYS.index())).isTrue();
            assertThat(repeat.getCell().getUserEnteredValue().getNumberValue()).isEqualTo(30d);
        });
    }

    @Test
    void resetsTheInputsOfStoresAndTheFactsOfEveryRow() {
        final int storeRow = 3;
        for (final CoreSheetColumn column : List.of(CoreSheetColumn.SALES_PLAN, CoreSheetColumn.AVG_CHECK_PLAN,
                CoreSheetColumn.ITEMS_PER_CHECK_PLAN, CoreSheetColumn.FIRST_DAY, CoreSheetColumn.LAST_DAY)) {
            assertThat(cleared(storeRow, column.index())).as(column.name()).isTrue();
        }
        for (final int row : List.of(2, storeRow, 4)) {
            for (final CoreSheetColumn column : List.of(CoreSheetColumn.AVG_CHECK_FACT, CoreSheetColumn.LOYALTY_AVG_CHECK_FACT,
                    CoreSheetColumn.ITEMS_PER_CHECK_FACT, CoreSheetColumn.MARGIN, CoreSheetColumn.STOCK_A, CoreSheetColumn.STOCK_D)) {
                assertThat(cleared(row, column.index())).as(column.name() + " row " + row).isTrue();
            }
        }
    }

    @Test
    void keepsTheFormulasOfSummaryRowsAndTheStoreNames() {
        for (final int summaryRow : List.of(2, 4)) {
            for (final CoreSheetColumn column : List.of(CoreSheetColumn.SALES_PLAN, CoreSheetColumn.AVG_CHECK_PLAN,
                    CoreSheetColumn.ITEMS_PER_CHECK_PLAN, CoreSheetColumn.FIRST_DAY, CoreSheetColumn.LAST_DAY)) {
                assertThat(cleared(summaryRow, column.index())).as(column.name() + " row " + summaryRow).isFalse();
            }
        }
        assertThat(cleared(3, 0)).isFalse();
    }
}
