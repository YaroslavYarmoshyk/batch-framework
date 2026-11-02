package com.etake.coresheetrenewal.sheet;

import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.google.api.services.sheets.v4.model.Request;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SheetStructureEditorTest {
    private static final int SHEET_ID = 1;
    private static final int FIRST_ROW_INDEX = CoreSheetLayout.FIRST_DATA_ROW - 1;
    private static final SummaryRowTemplates TEMPLATES = new SummaryRowTemplates(
            List.of("Магазин", "=SUM(AI4:BM4)", "значення"), List.of("Регіон", "=SUM(B4:B5)"), List.of("=\"Разом\"", "=SUM(B3;B6)"));
    private static final LocalDate NOVEMBER = LocalDate.of(2026, 11, 1);

    /** Every store of the layout, selling up to the end of October. */
    private static final Map<String, StorePerformance> ALL_SELLING = Stream.of("А", "Б", "В", "Г", "Д")
            .collect(Collectors.toMap(Function.identity(), store -> selling(store, LocalDate.of(2026, 10, 31))));

    private static StorePerformance selling(final String store, final LocalDate... days) {
        return new StorePerformance(store, Arrays.stream(days).collect(Collectors.toMap(Function.identity(), day -> BigDecimal.TEN)),
                null, MonthlyTotals.empty(store), null);
    }

    private static CoreSheetLayout layout() {
        return CoreSheetLayout.parse(
                List.of("Північ", "А", "Б", "В", "Південь", "Г", "Захід", "Д", "=\"Разом\""),
                List.of("=SUM(AI4:AI6)", "", "", "", "=SUM(AI8:AI8)", "", "=SUM(AI10:AI10)", "", "=SUM(AI3;AI7;AI9)"));
    }

    private static StoreLocation at(final String store, final String region) {
        return new StoreLocation(store, region, null);
    }

    /** Applies the row requests the way the Sheets API does, to prove their indexes hold for the final layout. */
    private static List<String> apply(final List<Request> requests, final CoreSheetLayout before) {
        final List<String> names = new ArrayList<>(before.rows().stream().map(CoreSheetRow::name).toList());
        for (final Request request : requests) {
            if (request.getInsertDimension() != null) {
                names.add(request.getInsertDimension().getRange().getStartIndex() - FIRST_ROW_INDEX, "?");
            } else if (request.getDeleteDimension() != null) {
                names.remove(request.getDeleteDimension().getRange().getStartIndex() - FIRST_ROW_INDEX);
            } else if (request.getMoveDimension() != null) {
                final int source = request.getMoveDimension().getSource().getStartIndex() - FIRST_ROW_INDEX;
                final int end = request.getMoveDimension().getSource().getEndIndex() - FIRST_ROW_INDEX;
                final int destination = request.getMoveDimension().getDestinationIndex() - FIRST_ROW_INDEX;
                final List<String> moved = new ArrayList<>(names.subList(source, end));
                names.subList(source, end).clear();
                names.addAll(destination > source ? destination - moved.size() : destination, moved);
            } else if (request.getUpdateCells() != null) {
                final String text = request.getUpdateCells().getRows().getFirst().getValues().getFirst().getUserEnteredValue().getStringValue();
                names.set(request.getUpdateCells().getRange().getStartRowIndex() - FIRST_ROW_INDEX, text);
            }
        }
        return names;
    }

    private static void assertRowsMatch(final SheetStructureEditor editor, final CoreSheetLayout before) {
        assertThat(apply(editor.finish(), before))
                .isEqualTo(editor.layout().rows().stream().map(CoreSheetRow::name).toList());
    }

    private static List<String> names(final SheetStructureEditor editor) {
        return editor.layout().rows().stream().map(CoreSheetRow::name).toList();
    }

    @Test
    void movesARowDownToTheEndOfTheNewRegion() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("А", at("А", "Південь")), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Північ", "Б", "В", "Південь", "Г", "А", "Захід", "Д", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void movesARowUpToTheEndOfTheNewRegion() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Д", at("Д", "Північ")), ALL_SELLING, Set.of());

        // Захід is left without stores and goes away.
        assertThat(names(editor)).containsExactly("Північ", "А", "Б", "В", "Д", "Південь", "Г", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void removesClosedStoresAndTheRegionsLeftEmpty() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of(
                "Б", new StoreLocation("Б", "Північ", LocalDate.of(2026, 10, 15)),
                "Г", new StoreLocation("Г", "Південь", LocalDate.of(2026, 10, 31))), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Північ", "А", "В", "Захід", "Д", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void keepsAStoreClosingDuringTheNewMonth() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", new StoreLocation("Б", "Північ", NOVEMBER)), ALL_SELLING, Set.of());

        assertThat(editor.hasChanges()).isFalse();
    }

    @Test
    void createsARegionMissingFromTheSheetBeforeTheCompanyRow() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", at("Б", "Шептицький")), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Північ", "А", "В", "Південь", "Г", "Захід", "Д", "Шептицький", "Б", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void leavesRowsAloneWhenTheDatabaseKnowsNothingBetter() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", at("Б", null), "А", at("А", "Північ")), ALL_SELLING, Set.of());

        assertThat(editor.hasChanges()).isFalse();
    }

    @Test
    void addsAStoreAtTheEndOfItsRegionWithoutTheValuesOfTheTemplateRow() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.addMissingStores(editor, before, List.of("А", "Новий"), Map.of("Новий", at("Новий", "Південь")), NOVEMBER);

        assertThat(names(editor)).containsExactly("Північ", "А", "Б", "В", "Південь", "Г", "Новий", "Захід", "Д", "=\"Разом\"");
        assertRowsMatch(editor, before);

        final int newRow = editor.layout().rows().get(6).number() - 1;
        assertThat(editor.finish().stream().map(Request::getRepeatCell).filter(Objects::nonNull).toList()).anySatisfy(repeat -> {
            assertThat(repeat.getRange().getStartRowIndex()).isEqualTo(newRow);
            assertThat(repeat.getRange().getStartColumnIndex()).isEqualTo(2);
            assertThat(repeat.getCell().getUserEnteredValue()).isNull();
        });
    }

    @Test
    void removesAStoreWithoutSalesOnTheLastTwoDaysEvenWithoutACloseDate() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());
        final Map<String, StorePerformance> performance = new HashMap<>(ALL_SELLING);
        performance.put("Б", selling("Б", LocalDate.of(2026, 10, 29)));
        performance.remove("В");

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER,
                Map.of("Б", at("Б", "Північ"), "В", at("В", "Північ")), performance, Set.of());

        assertThat(names(editor)).containsExactly("Північ", "А", "Південь", "Г", "Захід", "Д", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void keepsAStoreWithSalesOnOneOfTheLastTwoDays() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());
        final Map<String, StorePerformance> performance = new HashMap<>(ALL_SELLING);
        performance.put("Б", selling("Б", LocalDate.of(2026, 10, 30)));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", at("Б", "Північ")), performance, Set.of());

        assertThat(editor.hasChanges()).isFalse();
    }

    @Test
    void keepsAStoreWithoutRecentSalesWhenItHasAPlanForTheNewMonth() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());
        final Map<String, StorePerformance> performance = new HashMap<>(ALL_SELLING);
        performance.remove("Б");

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", at("Б", "Північ")), performance, Set.of("Б"));

        assertThat(editor.hasChanges()).isFalse();
    }

    @Test
    void aPlanDoesNotKeepAStoreWithACloseDate() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER,
                Map.of("Б", new StoreLocation("Б", "Північ", LocalDate.of(2026, 10, 15))), ALL_SELLING, Set.of("Б"));

        assertThat(names(editor)).doesNotContain("Б");
        assertRowsMatch(editor, before);
    }

    @Test
    void aStoreKeptForItsPlanStillMovesToItsNewRegion() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());
        final Map<String, StorePerformance> performance = new HashMap<>(ALL_SELLING);
        performance.remove("Б");

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", at("Б", "Південь")), performance, Set.of("Б"));

        assertThat(names(editor)).containsExactly("Північ", "А", "В", "Південь", "Г", "Б", "Захід", "Д", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void aLastDayWithoutSalesAnywhereMeansTheTransactionsAreNotLoaded() {
        final Map<String, StorePerformance> performance = Map.of(
                "А", selling("А", LocalDate.of(2026, 10, 30)),
                "Б", selling("Б", LocalDate.of(2026, 10, 30)));

        assertThatThrownBy(() -> StoreStructureSync.requireLastDaysLoaded(NOVEMBER, performance))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2026-10-31");
    }

    @Test
    void lastDaysWithSalesSomewherePassTheLoadCheck() {
        assertThatCode(() -> StoreStructureSync.requireLastDaysLoaded(NOVEMBER, Map.of(
                "А", selling("А", LocalDate.of(2026, 10, 30)),
                "Б", selling("Б", LocalDate.of(2026, 10, 31))))).doesNotThrowAnyException();
    }

    @Test
    void aNewRegionGoesBeforeTheFirstRegionSortedAfterIt() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES,
                Map.of("Північ", 10, "Шептицький", 15, "Південь", 20, "Захід", 30));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("Б", at("Б", "Шептицький")), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Північ", "А", "В", "Шептицький", "Б", "Південь", "Г", "Захід", "Д", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void aNewRegionSortedLastOrWithoutAnOrderGoesBeforeTheCompanyRow() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES,
                Map.of("Північ", 10, "Південь", 20, "Захід", 30, "Шептицький", 40));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER,
                Map.of("Б", at("Б", "Шептицький"), "В", at("В", "Без порядку")), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Північ", "А", "Південь", "Г", "Захід", "Д", "Шептицький", "Б", "Без порядку", "В", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void existingRegionsAreSortedWithTheirStores() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES,
                Map.of("Північ", 30, "Південь", 20, "Захід", 10));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of("А", at("А", "Північ")), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Захід", "Д", "Південь", "Г", "Північ", "А", "Б", "В", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void regionsWithoutASortOrderGoLastKeepingTheirOrder() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of("Захід", 1));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of(), ALL_SELLING, Set.of());

        assertThat(names(editor)).containsExactly("Захід", "Д", "Північ", "А", "Б", "В", "Південь", "Г", "=\"Разом\"");
        assertRowsMatch(editor, before);
    }

    @Test
    void sortedRegionsNeedNoChanges() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES,
                Map.of("Північ", 1, "Південь", 2, "Захід", 3));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of(), ALL_SELLING, Set.of());

        assertThat(editor.hasChanges()).isFalse();
    }

    @Test
    void theSummaryFormulasFollowTheSortedRows() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES,
                Map.of("Північ", 30, "Південь", 20, "Захід", 10));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER, Map.of(), ALL_SELLING, Set.of());

        final List<String> formulas = editor.finish().stream()
                .map(Request::getRepeatCell)
                .filter(Objects::nonNull)
                .map(repeat -> repeat.getCell().getUserEnteredValue())
                .filter(Objects::nonNull)
                .map(value -> value.getFormulaValue())
                .toList();
        assertThat(formulas).contains("=SUM(B4:B4)", "=SUM(B6:B6)", "=SUM(B8:B10)", "=SUM(B3;B5;B7)");
    }

    @Test
    void theRegionsOfTheDatabaseKeepTheirOrderWhenSheptytskyiIsSplitOffLviv3() {
        final CoreSheetLayout before = CoreSheetLayout.parse(
                List.of("Львів 1", "а", "Львів 2", "б", "Львів 3", "в", "Шептицький Епіцентр", "Львів 4", "г", "Тернопіль", "д", "Рівне", "е", "Волинь 1", "є", "Волинь 2", "ж", "=\"Разом\""),
                List.of("=S", "", "=S", "", "=S", "", "", "=S", "", "=S", "", "=S", "", "=S", "", "=S", "", "=S"));
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of(
                "Львів 1", 1, "Львів 2", 2, "Львів 3", 3, "Львів 4", 4, "Шептицький", 5,
                "Тернопіль", 6, "Рівне", 7, "Волинь 1", 8, "Волинь 2", 9, "Ужгород", 99));

        StoreStructureSync.applyToNewMonth(editor, before, NOVEMBER,
                Map.of("Шептицький Епіцентр", at("Шептицький Епіцентр", "Шептицький")),
                Map.of("Шептицький Епіцентр", selling("Шептицький Епіцентр", LocalDate.of(2026, 10, 31))), Set.of("а", "б", "в", "г", "д", "е", "є", "ж"));

        assertThat(editor.layout().rows().stream().filter(row -> row.kind() == CoreSheetRow.Kind.REGION).map(CoreSheetRow::name))
                .containsExactly("Львів 1", "Львів 2", "Львів 3", "Львів 4", "Шептицький", "Тернопіль", "Рівне", "Волинь 1", "Волинь 2");
        assertRowsMatch(editor, before);
    }

    @Test
    void aMissingStoreOfARegionWithoutOpenStoresIsSkipped() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.addMissingStores(editor, before, List.of("Склад"), Map.of(
                "Склад", new StoreLocation("Склад", "Внутрішній", LocalDate.of(2026, 10, 15))), NOVEMBER);

        assertThat(editor.hasChanges()).isFalse();
    }

    @Test
    void aMissingStoreIsAddedWhenAnotherStoreKeepsItsRegionOpen() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.addMissingStores(editor, before, List.of("Закритий"), Map.of(
                "Закритий", new StoreLocation("Закритий", "Шептицький", LocalDate.of(2026, 10, 15)),
                "Відкритий", at("Відкритий", "Шептицький")), NOVEMBER);

        assertThat(names(editor)).contains("Шептицький", "Закритий");
        assertRowsMatch(editor, before);
    }

    @Test
    void aMissingStoreJoinsARegionOnTheSheetEvenIfItIsClosed() {
        final CoreSheetLayout before = layout();
        final SheetStructureEditor editor = new SheetStructureEditor(SHEET_ID, before, TEMPLATES, Map.of());

        StoreStructureSync.addMissingStores(editor, before, List.of("Закритий"), Map.of(
                "Закритий", new StoreLocation("Закритий", "Південь", LocalDate.of(2026, 10, 15))), NOVEMBER);

        assertThat(names(editor)).contains("Закритий");
        assertRowsMatch(editor, before);
    }
}
