package com.etake.coresheetrenewal.service;

import com.etake.coresheetrenewal.model.MonthlyTotals;
import com.etake.coresheetrenewal.model.StoreLocation;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.googlesheets.GoogleSheetsGateway;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreSheetRenewalServiceTest {
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);
    private static final Map<String, StorePerformance> SELLING = Map.of("Магазин", selling("Магазин"));

    private static StorePerformance selling(final String store) {
        return new StorePerformance(store, Map.of(
                LocalDate.of(2026, 10, 30), BigDecimal.TEN,
                LocalDate.of(2026, 10, 31), BigDecimal.TEN), null, MonthlyTotals.empty(store), null);
    }

    private final GoogleSheetsGateway gateway = mock(GoogleSheetsGateway.class);
    private final CoreSheetRenewalService service = new CoreSheetRenewalService(gateway);

    @BeforeEach
    void sheetLayout() {
        final List<ValueRange> columns = List.of(
                new ValueRange().setValues(List.of(List.of("Регіон"), List.of("Магазин"), List.of("=\"Разом\""))),
                new ValueRange().setValues(List.of(List.of("=SUM(AI4:AI4)"), List.of(), List.of("=SUM(AI3)"))));
        final List<ValueRange> templates = List.of(
                new ValueRange().setValues(List.of(List.of("Магазин", "=SUM(C4:C4)"))),
                new ValueRange().setValues(List.of(List.of("Регіон", "=SUM(C4:C4)"))),
                new ValueRange().setValues(List.of(List.of("=\"Разом\"", "=SUM(C3;C3)"))));
        when(gateway.readFormulas(anyList())).thenAnswer(call ->
                call.<List<String>>getArgument(0).size() == 2 ? columns : templates);
    }

    private void spreadsheetWith(final String... titles) {
        int index = 0;
        final List<Sheet> sheets = new ArrayList<>();
        for (final String title : titles) {
            sheets.add(new Sheet().setProperties(new SheetProperties().setSheetId(index + 10).setTitle(title).setIndex(index)));
            index++;
        }
        when(gateway.getSpreadsheet(anyString())).thenReturn(new Spreadsheet().setSheets(sheets));
    }

    @Test
    @SuppressWarnings("unchecked")
    void readsOnlyTheFinishedMonthsCellsAndFillsItBeforeCreatingTheNewSheet() {
        spreadsheetWith("Months", "Жовтень 26", "Вересень 26");

        service.renew(NOVEMBER, SELLING, Map.of(), Set.of(), Map.of());

        final ArgumentCaptor<List<String>> reads = ArgumentCaptor.forClass(List.class);
        verify(gateway, times(2)).readFormulas(reads.capture());
        assertThat(reads.getAllValues()).flatExtracting(ranges -> ranges).allMatch(range -> range.startsWith("'Жовтень 26'!"));
        verify(gateway, never()).readValues(anyList());

        final InOrder order = inOrder(gateway);
        order.verify(gateway).writeValues(anyList());
        final ArgumentCaptor<List<Request>> update = ArgumentCaptor.forClass(List.class);
        order.verify(gateway).batchUpdate(update.capture());
        final var duplicate = update.getValue().getFirst().getDuplicateSheet();
        assertThat(duplicate.getSourceSheetId()).isEqualTo(11);
        assertThat(duplicate.getNewSheetName()).isEqualTo("Листопад 26");
        assertThat(duplicate.getInsertSheetIndex()).isEqualTo(1);
    }

    @Test
    void aRerunRefreshesTheActualsAndLeavesAnExistingNewSheetAlone() {
        spreadsheetWith("Months", "Листопад 26", "Жовтень 26");

        service.renew(NOVEMBER, SELLING, Map.of(), Set.of(), Map.of());

        verify(gateway).writeValues(anyList());
        verify(gateway, never()).batchUpdate(any());
    }

    @Test
    void failsBeforeWritingWhenTheFinishedMonthsSheetIsMissing() {
        spreadsheetWith("Months", "Вересень 26");

        assertThatThrownBy(() -> service.renew(NOVEMBER, SELLING, Map.of(), Set.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Жовтень 26");
        verify(gateway, never()).writeValues(anyList());
        verify(gateway, never()).batchUpdate(any());
    }

    @Test
    void aStoreWithSalesButNoRowOrRegionStopsTheJobBeforeAnyWrite() {
        spreadsheetWith("Months", "Жовтень 26");
        final StorePerformance unknown = selling("Новий");

        assertThatThrownBy(() -> service.renew(NOVEMBER, Map.of("Новий", unknown),
                Map.of("Новий", new StoreLocation("Новий", null, null)), Set.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Новий");
        verify(gateway, never()).writeValues(anyList());
        verify(gateway, never()).batchUpdate(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void aNewStoreGetsARowOnTheFinishedMonthBeforeItsActualsAreWritten() {
        spreadsheetWith("Months", "Листопад 26", "Жовтень 26");
        final StorePerformance added = selling("Новий");

        service.renew(NOVEMBER, Map.of("Новий", added), Map.of("Новий", new StoreLocation("Новий", "Регіон", null)), Set.of(), Map.of());

        final InOrder order = inOrder(gateway);
        final ArgumentCaptor<List<Request>> update = ArgumentCaptor.forClass(List.class);
        order.verify(gateway).batchUpdate(update.capture());
        order.verify(gateway).writeValues(anyList());
        assertThat(update.getValue()).extracting(Request::getInsertDimension).anyMatch(Objects::nonNull);
    }

    @Test
    void failsBeforeWritingWhenTheLastDaysAreNotLoaded() {
        spreadsheetWith("Months", "Жовтень 26");

        assertThatThrownBy(() -> service.renew(NOVEMBER, Map.of(), Map.of(), Set.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("transactions loaded");
        verify(gateway, never()).writeValues(anyList());
        verify(gateway, never()).batchUpdate(any());
    }
}
