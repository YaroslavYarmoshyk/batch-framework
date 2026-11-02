package com.etake.promotionforecast.service;

import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import com.etake.promotionforecast.config.properties.PromotionProperties;
import com.etake.promotionforecast.model.ReportRow;
import com.etake.promotionforecast.model.enumeration.PromotionType;
import com.etake.promotionforecast.writer.googlesheets.GoogleSheetsGateway;
import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TotalColumn;
import com.etake.promotionforecast.writer.googlesheets.SheetRows;
import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.DeveloperMetadata;
import com.google.api.services.sheets.v4.model.GridData;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionSheetsLayoutServiceTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 28);
    private static final LocalDate END = LocalDate.of(2026, 11, 10);
    private static final String CURRENT_PROMOTION = "CYCLIC:2026-10-28/2026-11-10";
    private static final int SOURCE_ID = 1;
    private static final int TOTAL_ID = 2;
    private static final int FORECAST_ID = 3;
    private static final int EXPORT_ID = 4;
    private static final int OLD_MANAGER_ID = 5;

    private final GoogleSheetsGateway gateway = mock(GoogleSheetsGateway.class);
    private final PromotionProperties promotion = new PromotionProperties(PromotionType.CYCLIC, START, END, Period.ofYears(2), 4);

    private final List<ReportRow> rows = List.of(
            row("Федюра Олександр", "Львів Обнова", "59239"),
            row("Вантух Олеся", "Львів Обнова", "44979"),
            row("Федюра Олександр", "Луцьк Л Українки", "59239"),
            row(null, "Рівне Базар", "115835"));

    /** The state from the screenshot: the cyclic-action template with dropdowns still in Q, S, T and 20 columns. */
    @Test
    void laysOutTheTemplateWhateverStateItIsIn() {
        givenSpreadsheet(null);
        givenTemplateDropdowns(16, 18, 19);

        service(false).rebuild(rows);

        final List<Request> structure = capturedBatches().getFirst();
        assertThat(structure).extracting(Request::getDeleteSheet).filteredOn(Objects::nonNull)
                .singleElement().satisfies(delete -> assertThat(delete.getSheetId()).isEqualTo(OLD_MANAGER_ID));

        // Dropdowns (with their chip colours) are copied from Q / T into M, O and P …
        assertThat(structure).extracting(Request::getCopyPaste).filteredOn(Objects::nonNull)
                .allSatisfy(copy -> assertThat(copy.getPasteType()).isEqualTo("PASTE_DATA_VALIDATION"))
                .extracting(copy -> copy.getSource().getStartColumnIndex(), copy -> copy.getDestination().getStartColumnIndex())
                .containsExactly(tuple(16, 12), tuple(16, 14), tuple(19, 15));
        // … before the columns beyond P are deleted.
        assertThat(structure.indexOf(structure.stream().filter(request -> request.getDeleteDimension() != null).findFirst().orElseThrow()))
                .isGreaterThan(structure.indexOf(structure.stream().filter(request -> request.getCopyPaste() != null).toList().getLast()));
        assertThat(structure).extracting(Request::getDeleteDimension).filteredOn(Objects::nonNull)
                .singleElement().satisfies(delete -> {
                    assertThat(delete.getRange().getSheetId()).isEqualTo(TOTAL_ID);
                    assertThat(delete.getRange().getDimension()).isEqualTo("COLUMNS");
                    assertThat(delete.getRange().getStartIndex()).isEqualTo(16);
                    assertThat(delete.getRange().getEndIndex()).isEqualTo(20);
                });

        // Header, widths, filter and frozen columns are always set by the job.
        assertThat(structure).extracting(Request::getUpdateCells).filteredOn(Objects::nonNull)
                .singleElement().satisfies(header -> assertThat(header.getRows().getFirst().getValues())
                        .extracting(cell -> cell.getUserEnteredValue().getStringValue())
                        .containsExactlyElementsOf(Arrays.stream(TotalColumn.values()).map(TotalColumn::getHeader).toList()));
        assertThat(structure).extracting(Request::getUpdateDimensionProperties).filteredOn(Objects::nonNull)
                .filteredOn(update -> "COLUMNS".equals(update.getRange().getDimension()))
                .extracting(update -> update.getProperties().getPixelSize())
                .containsExactly(21, 181, 28, 28, 28, 59, 227, 35, 35, 58, 134, 101, 114, 91, 114, 91);
        assertThat(structure).extracting(Request::getSetBasicFilter).filteredOn(Objects::nonNull)
                .singleElement().satisfies(filter -> assertThat(filter.getFilter().getRange().getEndColumnIndex()).isEqualTo(16));
        assertThat(structure).extracting(Request::getUpdateSheetProperties).filteredOn(Objects::nonNull)
                .extracting(update -> update.getProperties().getSheetId(), update -> update.getFields())
                .containsExactly(tuple(TOTAL_ID, "gridProperties.frozenColumnCount"), tuple(SOURCE_ID, "hidden"));

        assertThat(structure).extracting(Request::getDuplicateSheet).filteredOn(Objects::nonNull)
                .allSatisfy(duplicate -> assertThat(duplicate.getSourceSheetId()).isEqualTo(TOTAL_ID))
                .extracting(duplicate -> duplicate.getNewSheetName())
                .containsExactly("Вантух Олеся", "Федюра Олександр");
        assertThat(structure.indexOf(structure.stream().filter(request -> request.getDuplicateSheet() != null).findFirst().orElseThrow()))
                .isGreaterThan(structure.indexOf(structure.stream().filter(request -> request.getSetBasicFilter() != null).findFirst().orElseThrow()));

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<SheetRows>> blocks = ArgumentCaptor.forClass(List.class);
        verify(gateway).writeRows(blocks.capture());
        assertThat(blocks.getValue())
                .extracting(SheetRows::sheetName, block -> block.rows().size())
                .containsExactly(
                        tuple("Загальна акція", 4),
                        tuple("Вантух Олеся", 1),
                        tuple("Федюра Олександр", 2),
                        tuple("Прогноз", 3));
        assertThat(blocks.getValue().getFirst().rows().getFirst()).containsExactly(
                "Федюра Олександр", "Львів Обнова", "7", 4, "3-Маслини та оливки", "59239",
                "Product 59239", 1, 0.36, 5L);

        final List<Request> finishing = capturedBatches().getLast();
        assertThat(finishing).extracting(Request::getRepeatCell).filteredOn(Objects::nonNull)
                .filteredOn(repeat -> repeat.getRange().getSheetId() == TOTAL_ID
                        && repeat.getRange().getStartColumnIndex() == TotalColumn.ADJUSTED_QTY.index())
                .extracting(repeat -> repeat.getCell().getUserEnteredValue())
                .filteredOn(Objects::nonNull)
                .extracting(value -> value.getFormulaValue())
                .containsExactly("=IFERROR(J2/SUMIF(F:F;F2;J:J)*VLOOKUP(F2;'Прогноз'!A:F;6;0);\"\")");
        assertThat(finishing).extracting(Request::getCreateDeveloperMetadata).filteredOn(Objects::nonNull)
                .singleElement().satisfies(create -> {
                    assertThat(create.getDeveloperMetadata().getMetadataKey()).isEqualTo(PromotionSheetsLayoutService.PROMOTION_METADATA_KEY);
                    assertThat(create.getDeveloperMetadata().getMetadataValue()).isEqualTo(CURRENT_PROMOTION);
                });
    }

    @Test
    void createsPlainDropdownsWhenTheTemplateHasNone() {
        givenSpreadsheet(null);
        givenTemplateDropdowns();

        service(false).rebuild(rows);

        assertThat(capturedBatches().getFirst()).extracting(Request::getSetDataValidation).filteredOn(Objects::nonNull)
                .filteredOn(validation -> validation.getRule() != null)
                .extracting(validation -> validation.getRange().getStartColumnIndex(),
                        validation -> validation.getRule().getCondition().getValues().stream().map(ConditionValue::getUserEnteredValue).toList())
                .containsExactly(
                        tuple(12, List.of("Так", "Ні")),
                        tuple(14, List.of("Так", "Ні")),
                        tuple(15, List.of("100%", "70%")));
    }

    @Test
    void skipsWhenManagersAlreadyEnteredDataForThisPromotion() {
        givenSpreadsheet(CURRENT_PROMOTION);
        when(gateway.readValues(anyList())).thenReturn(List.of(values(List.of()), values(List.of(12))));

        service(false).rebuild(rows);

        verify(gateway, never()).batchUpdate(anyList());
        verify(gateway, never()).writeRows(anyList());
    }

    @Test
    void rebuildsANewPromotionDespitePreviousInput() {
        givenSpreadsheet("CYCLIC:2026-10-01/2026-10-14");
        givenTemplateDropdowns(12, 14, 15);

        service(false).rebuild(rows);

        final List<Request> finishing = capturedBatches().getLast();
        assertThat(finishing).extracting(Request::getUpdateDeveloperMetadata).filteredOn(Objects::nonNull)
                .singleElement().satisfies(update -> {
                    assertThat(update.getDeveloperMetadata().getMetadataValue()).isEqualTo(CURRENT_PROMOTION);
                    assertThat(update.getDataFilters().getFirst().getDeveloperMetadataLookup().getMetadataId()).isEqualTo(77);
                });
    }

    @Test
    void forceRebuildsDespiteManualInput() {
        givenSpreadsheet(CURRENT_PROMOTION);
        givenTemplateDropdowns(12, 14, 15);

        service(true).rebuild(rows);

        verify(gateway, times(2)).batchUpdate(anyList());
        verify(gateway).writeRows(anyList());
    }

    @Test
    void doesNothingWhenLayoutIsDisabled() {
        final GoogleSheetsProperties disabled = new GoogleSheetsProperties(true, "key.json", "sheet-id", "result",
                new GoogleSheetsProperties.Layout(false, false));

        new PromotionSheetsLayoutService(gateway, disabled, promotion).rebuild(rows);

        verify(gateway, never()).getSpreadsheet(anyString());
    }

    private void givenSpreadsheet(final String lastPromotion) {
        final List<DeveloperMetadata> metadata = lastPromotion == null ? null : List.of(new DeveloperMetadata()
                .setMetadataId(77)
                .setMetadataKey(PromotionSheetsLayoutService.PROMOTION_METADATA_KEY)
                .setMetadataValue(lastPromotion));
        when(gateway.getSpreadsheet(anyString())).thenReturn(new Spreadsheet()
                .setProperties(new SpreadsheetProperties().setLocale("ru_RU"))
                .setDeveloperMetadata(metadata)
                .setSheets(List.of(
                        sheet(SOURCE_ID, "result"),
                        sheet(TOTAL_ID, "Загальна акція"),
                        sheet(FORECAST_ID, "Прогноз"),
                        sheet(EXPORT_ID, "Заливка в 1С"),
                        sheet(OLD_MANAGER_ID, "Скірка Оксана"))));
    }

    private PromotionSheetsLayoutService service(final boolean force) {
        final GoogleSheetsProperties properties = new GoogleSheetsProperties(true, "key.json", "sheet-id", "result",
                new GoogleSheetsProperties.Layout(true, force));
        return new PromotionSheetsLayoutService(gateway, properties, promotion);
    }

    @SuppressWarnings("unchecked")
    private List<List<Request>> capturedBatches() {
        final ArgumentCaptor<List<Request>> captor = ArgumentCaptor.forClass(List.class);
        verify(gateway, times(2)).batchUpdate(captor.capture());
        return captor.getAllValues();
    }

    private static ValueRange values(final List<Object> row) {
        return new ValueRange().setValues(List.of(row));
    }

    private static Sheet sheet(final int id, final String title) {
        return new Sheet().setProperties(new SheetProperties().setSheetId(id).setTitle(title)
                .setGridProperties(new GridProperties().setRowCount(5483).setColumnCount(20)));
    }

    /** Row 2 of «Загальна акція»: «Так/Ні» dropdowns in the first two columns given, «100%/70%» in the third. */
    private void givenTemplateDropdowns(final int... columns) {
        final List<CellData> cells = new ArrayList<>(Collections.nCopies(20, new CellData()));
        for (int i = 0; i < columns.length; i++) {
            cells.set(columns[i], new CellData().setDataValidation(dropdown(i < 2 ? List.of("Так", "Ні") : List.of("100%", "70%"))));
        }
        when(gateway.getSpreadsheet(anyString(), anyList())).thenReturn(new Spreadsheet().setSheets(List.of(new Sheet()
                .setData(List.of(new GridData().setRowData(List.of(new RowData().setValues(cells))))))));
    }

    private static DataValidationRule dropdown(final List<String> values) {
        return new DataValidationRule().setCondition(new BooleanCondition()
                .setType("ONE_OF_LIST")
                .setValues(values.stream().map(value -> new ConditionValue().setUserEnteredValue(value)).toList()));
    }

    private static ReportRow row(final String manager, final String store, final String product) {
        return new ReportRow(manager, store, "7", product, "Product " + product, "3-Маслини та оливки",
                PromotionType.CYCLIC.getName(), START, END, 4, 1, null, null, null, null, null, null,
                new BigDecimal("0.36"), 5L);
    }
}
