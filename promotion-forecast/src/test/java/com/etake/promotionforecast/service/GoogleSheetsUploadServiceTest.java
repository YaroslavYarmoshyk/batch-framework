package com.etake.promotionforecast.service;

import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.googlesheets.SheetRows;
import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import com.etake.promotionforecast.model.ReportColumn;
import com.etake.promotionforecast.model.ReportRow;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleSheetsUploadServiceTest {
    private static final int RESULT_SHEET_ID = 42;

    private final GoogleSheetsGateway gateway = mock(GoogleSheetsGateway.class);
    private final GoogleSheetsUploadService service = new GoogleSheetsUploadService(gateway,
            new GoogleSheetsProperties(true, "key.json", "sheet-id", "result", new GoogleSheetsProperties.Layout(true, false)));

    /** A grid of 5 000 rows rejects a write to row 5 001, so the sheet must grow before the values are written. */
    @Test
    void growsTheSheetBeforeWritingMoreRowsThanItHas() {
        givenResultSheet(new GridProperties().setRowCount(5_000).setColumnCount(10));
        final List<ReportRow> rows = Collections.nCopies(5_483, row());

        service.upload(rows);

        final InOrder order = inOrder(gateway);
        final ArgumentCaptor<List<Request>> requests = requestsCaptor();
        order.verify(gateway).batchUpdate(requests.capture());
        order.verify(gateway).writeRows(anyList());
        assertThat(requests.getValue())
                .extracting(request -> request.getAppendDimension().getSheetId(),
                        request -> request.getAppendDimension().getDimension(),
                        request -> request.getAppendDimension().getLength())
                .containsExactly(
                        tuple(RESULT_SHEET_ID, "ROWS", 5_484 - 5_000),
                        tuple(RESULT_SHEET_ID, "COLUMNS", ReportColumn.values().length - 10));
    }

    @Test
    void leavesALargeEnoughSheetAsItIs() {
        givenResultSheet(new GridProperties().setRowCount(10_000).setColumnCount(26));

        service.upload(List.of(row()));

        final ArgumentCaptor<List<Request>> requests = requestsCaptor();
        final ArgumentCaptor<List<SheetRows>> blocks = blocksCaptor();
        final InOrder order = inOrder(gateway);
        order.verify(gateway).batchUpdate(requests.capture());
        order.verify(gateway).writeRows(blocks.capture());
        assertThat(requests.getValue()).isEmpty();
        assertThat(blocks.getValue().getFirst().rows()).hasSize(2);
    }

    private void givenResultSheet(final GridProperties grid) {
        when(gateway.getSpreadsheet(anyString())).thenReturn(new Spreadsheet().setSheets(List.of(
                new Sheet().setProperties(new SheetProperties().setSheetId(RESULT_SHEET_ID).setTitle("result").setGridProperties(grid)))));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Request>> requestsCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<SheetRows>> blocksCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static ReportRow row() {
        return new ReportRow("Вантух Олеся", "Магазин 1", "M", "100", "Товар", "3-Підкатегорія", "Акція",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 14), 0, 1,
                null, null, null, null, null, null, BigDecimal.ONE, 14L);
    }
}
