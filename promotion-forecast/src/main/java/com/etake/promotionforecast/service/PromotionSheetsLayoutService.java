package com.etake.promotionforecast.service;

import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import com.etake.promotionforecast.config.properties.PromotionProperties;
import com.etake.promotionforecast.model.ReportRow;
import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.promotionforecast.writer.googlesheets.LayoutFormulas;
import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.ForecastColumn;
import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TotalColumn;
import com.etake.googlesheets.SheetCells;
import com.etake.googlesheets.SheetRows;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.ColorStyle;
import com.google.api.services.sheets.v4.model.CreateDeveloperMetadataRequest;
import com.google.api.services.sheets.v4.model.DataFilter;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.DeveloperMetadata;
import com.google.api.services.sheets.v4.model.DeveloperMetadataLocation;
import com.google.api.services.sheets.v4.model.DeveloperMetadataLookup;
import com.google.api.services.sheets.v4.model.GridData;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.NumberFormat;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.UpdateDeveloperMetadataRequest;
import com.google.api.services.sheets.v4.model.ValueRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.FIRST_DATA_ROW;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.FORECAST_SHEET;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.FROZEN_COLUMNS;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.HEADER_HEIGHT_PX;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.HEADER_ROW;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.PERMANENT_SHEETS;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.SHIPMENT_SHARES;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TOTAL_SHEET;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.WIDTH;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.YES_NO;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.letter;
import static com.etake.googlesheets.SheetRequests.appendColumns;
import static com.etake.googlesheets.SheetRequests.appendRows;
import static com.etake.googlesheets.SheetRequests.basicFilter;
import static com.etake.googlesheets.SheetRequests.borders;
import static com.etake.googlesheets.SheetRequests.clear;
import static com.etake.googlesheets.SheetRequests.clearValidation;
import static com.etake.googlesheets.SheetRequests.columnWidth;
import static com.etake.googlesheets.SheetRequests.copyValidation;
import static com.etake.googlesheets.SheetRequests.deleteColumns;
import static com.etake.googlesheets.SheetRequests.deleteSheet;
import static com.etake.googlesheets.SheetRequests.duplicateSheet;
import static com.etake.googlesheets.SheetRequests.fillFormula;
import static com.etake.googlesheets.SheetRequests.fillNumber;
import static com.etake.googlesheets.SheetRequests.frozenColumns;
import static com.etake.googlesheets.SheetRequests.headerFormat;
import static com.etake.googlesheets.SheetRequests.hide;
import static com.etake.googlesheets.SheetRequests.inputFormat;
import static com.etake.googlesheets.SheetRequests.listValidation;
import static com.etake.googlesheets.SheetRequests.numberFormat;
import static com.etake.googlesheets.SheetRequests.openRange;
import static com.etake.googlesheets.SheetRequests.range;
import static com.etake.googlesheets.SheetRequests.rgb;
import static com.etake.googlesheets.SheetRequests.rowHeight;
import static com.etake.googlesheets.SheetRequests.texts;

/**
 * Rebuilds «Прогноз», «Загальна акція» and one sheet per manager from the forecast — what the «Залити акцію»
 * Apps Script used to do.
 *
 * <p>The job owns the layout of «Загальна акція»: headers, widths, filter, frozen columns and dropdowns are set on
 * every run, whatever state the template is in. Dropdowns are copied from wherever the template has them, so their
 * chip colours survive. Manager sheets are duplicates of «Загальна акція».
 *
 * <p>The rebuild wipes manual input, so it is skipped when the spreadsheet already holds the same promotion
 * (remembered in developer metadata) and managers have started entering data, unless {@code layout.force} is set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionSheetsLayoutService {
    static final String PROMOTION_METADATA_KEY = "promotion-forecast.promotion";
    private static final String SPREADSHEET_FIELDS = "properties.locale,"
            + "sheets.properties(sheetId,title,gridProperties(rowCount,columnCount)),"
            + "developerMetadata(metadataId,metadataKey,metadataValue)";
    private static final String VALIDATION_FIELDS = "sheets.data.rowData.values.dataValidation";
    private static final List<TotalColumn> MANAGER_INPUT_COLUMNS = List.of(
            TotalColumn.MANAGER_QTY, TotalColumn.INCLUDE_STOCK, TotalColumn.INCLUDE_MULTIPLICITY);
    private static final List<TotalColumn> YES_NO_COLUMNS = List.of(TotalColumn.INCLUDE_STOCK, TotalColumn.INCLUDE_MULTIPLICITY);
    private static final List<ForecastColumn> FORECAST_INPUT_COLUMNS = List.of(
            ForecastColumn.MANAGER_FORECAST, ForecastColumn.ADJUSTED_FORECAST, ForecastColumn.MULTIPLICITY);

    private static final NumberFormat DECIMAL_FORMAT = new NumberFormat().setType("NUMBER").setPattern("#,##0.00");
    private static final NumberFormat INTEGER_FORMAT = new NumberFormat().setType("NUMBER").setPattern("#,##0");
    private static final NumberFormat DEVIATION_FORMAT = new NumberFormat().setType("PERCENT").setPattern("0.0%");
    private static final NumberFormat SHARE_FORMAT = new NumberFormat().setType("PERCENT").setPattern("0%");
    private static final ColorStyle ANALYST_HEADER = rgb(0x43, 0x43, 0x43);
    private static final ColorStyle MANAGER_HEADER = rgb(0x27, 0x4e, 0x13);
    private static final ColorStyle INPUT_BACKGROUND = rgb(0xd9, 0xea, 0xd3);
    private static final double FIRST_SHIPMENT_SHARE = 0.7;

    private final GoogleSheetsGateway gateway;
    private final GoogleSheetsProperties properties;
    private final PromotionProperties promotionProperties;

    /** Size of «Загальна акція» and the columns its dropdowns currently sit in (row 2), if any. */
    private record Template(int sheetId, int rowCount, int columnCount, OptionalInt yesNoColumn, OptionalInt shareColumn) {
    }

    public void rebuild(final List<ReportRow> rows) {
        if (!properties.isConfigured() || !properties.layout().enabled()) {
            log.info("Promotion sheets rebuild skipped (disabled or not configured)");
            return;
        }
        if (rows.isEmpty()) {
            log.info("Promotion sheets rebuild skipped: no forecast rows");
            return;
        }

        final Spreadsheet spreadsheet = gateway.getSpreadsheet(SPREADSHEET_FIELDS);
        final Map<String, SheetProperties> sheets = spreadsheet.getSheets().stream()
                .map(Sheet::getProperties)
                .collect(Collectors.toMap(SheetProperties::getTitle, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        final SheetProperties totalSheet = requireSheet(sheets, TOTAL_SHEET);
        final int forecastSheetId = requireSheet(sheets, FORECAST_SHEET).getSheetId();
        final List<String> oldManagerSheets = sheets.keySet().stream().filter(title -> !isPermanent(title)).toList();
        final Optional<DeveloperMetadata> promotionMetadata = Optional.ofNullable(spreadsheet.getDeveloperMetadata())
                .orElse(List.of()).stream()
                .filter(metadata -> PROMOTION_METADATA_KEY.equals(metadata.getMetadataKey()))
                .findFirst();

        if (!properties.layout().force() && hasManualInputForCurrentPromotion(promotionMetadata, oldManagerSheets)) {
            log.warn("Promotion sheets rebuild skipped: the spreadsheet already holds promotion {} and managers have "
                    + "entered data. Set GOOGLE_SHEETS_LAYOUT_FORCE=true to rebuild anyway (manual input will be lost)",
                    promotionKey());
            return;
        }

        final Map<String, List<ReportRow>> rowsByManager = groupByManager(rows);
        final List<List<Object>> products = products(rows);
        final Template template = readTemplate(totalSheet);
        final LayoutFormulas formulas = LayoutFormulas.forLocale(spreadsheet.getProperties().getLocale());

        final Set<Integer> usedSheetIds = sheets.values().stream().map(SheetProperties::getSheetId).collect(Collectors.toCollection(HashSet::new));
        final Map<String, Integer> managerSheetIds = new LinkedHashMap<>();
        rowsByManager.keySet().forEach(manager -> managerSheetIds.put(manager, newSheetId(usedSheetIds)));

        final List<Request> structure = new ArrayList<>();
        oldManagerSheets.forEach(title -> structure.add(deleteSheet(sheets.get(title).getSheetId())));
        structure.addAll(templateRequests(template, rows.size()));
        structure.add(clear(openRange(forecastSheetId, FIRST_DATA_ROW, 0, null)));
        // Duplicated once the template is laid out and cleared, so manager sheets start from the header only.
        int insertIndex = sheets.size() - oldManagerSheets.size();
        for (final Map.Entry<String, Integer> manager : managerSheetIds.entrySet()) {
            structure.add(duplicateSheet(template.sheetId(), manager.getValue(), manager.getKey(), insertIndex++));
        }
        Optional.ofNullable(sheets.get(properties.sheetName())).ifPresent(source -> structure.add(hide(source.getSheetId())));

        gateway.batchUpdate(structure);
        gateway.writeRows(dataBlocks(rows, rowsByManager, products));

        final List<Request> finishing = formulaAndFormatRequests(template.sheetId(), forecastSheetId, rows.size(),
                products.size(), rowsByManager, managerSheetIds, formulas);
        // Remembered last, so a run that fails halfway does not protect a half-built spreadsheet.
        finishing.add(promotionMetadataRequest(promotionMetadata));
        gateway.batchUpdate(finishing);

        log.info("Rebuilt promotion sheets for {}: {} rows, {} products, {} manager sheets (formula separator '{}')",
                promotionKey(), rows.size(), products.size(), managerSheetIds.size(), formulas.separator());
    }

    // ---- safeguard -------------------------------------------------------------------------------------------

    /**
     * True when the spreadsheet was last built for the promotion being forecasted and any manager sheet or «Прогноз»
     * input column already has data — rebuilding would wipe it.
     */
    private boolean hasManualInputForCurrentPromotion(final Optional<DeveloperMetadata> promotionMetadata,
                                                      final List<String> managerSheets) {
        final boolean samePromotion = promotionMetadata
                .map(DeveloperMetadata::getMetadataValue)
                .filter(promotionKey()::equals)
                .isPresent();
        if (!samePromotion) {
            return false;
        }

        final List<String> ranges = new ArrayList<>();
        for (final String sheet : managerSheets) {
            MANAGER_INPUT_COLUMNS.forEach(column -> ranges.add(SheetCells.a1(sheet, columnRange(column.index()))));
        }
        FORECAST_INPUT_COLUMNS.forEach(column -> ranges.add(SheetCells.a1(FORECAST_SHEET, columnRange(column.index()))));

        return gateway.readValues(ranges).stream()
                .map(ValueRange::getValues)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .flatMap(List::stream)
                .anyMatch(value -> !SheetCells.isBlank(value));
    }

    /** Identifies the forecasted promotion, e.g. {@code CYCLIC:2026-10-28/2026-11-10}. */
    private String promotionKey() {
        return "%s:%s/%s".formatted(promotionProperties.type().name(), promotionProperties.startDate(), promotionProperties.endDate());
    }

    private Request promotionMetadataRequest(final Optional<DeveloperMetadata> existing) {
        return existing.map(developerMetadata -> new Request().setUpdateDeveloperMetadata(new UpdateDeveloperMetadataRequest()
                .setDataFilters(List.of(new DataFilter().setDeveloperMetadataLookup(
                        new DeveloperMetadataLookup().setMetadataId(developerMetadata.getMetadataId()))))
                .setDeveloperMetadata(new DeveloperMetadata().setMetadataValue(promotionKey()))
                .setFields("metadataValue"))).orElseGet(() -> new Request().setCreateDeveloperMetadata(new CreateDeveloperMetadataRequest()
                .setDeveloperMetadata(new DeveloperMetadata()
                        .setMetadataKey(PROMOTION_METADATA_KEY)
                        .setMetadataValue(promotionKey())
                        .setLocation(new DeveloperMetadataLocation().setSpreadsheet(true))
                        .setVisibility("DOCUMENT"))));
    }

    // ---- «Загальна акція» layout ----------------------------------------------------------------------------------

    private Template readTemplate(final SheetProperties totalSheet) {
        final GridProperties grid = totalSheet.getGridProperties();
        final List<CellData> secondRow = Optional.ofNullable(gateway.getSpreadsheet(VALIDATION_FIELDS, List.of(SheetCells.a1(TOTAL_SHEET, "2:2"))))
                .map(Spreadsheet::getSheets).filter(list -> !list.isEmpty()).map(List::getFirst)
                .map(Sheet::getData).filter(list -> !list.isEmpty()).map(List::getFirst)
                .map(GridData::getRowData).filter(list -> !list.isEmpty()).map(List::getFirst)
                .map(RowData::getValues)
                .orElse(List.of());
        return new Template(
                totalSheet.getSheetId(),
                grid.getRowCount(),
                grid.getColumnCount(),
                dropdownColumn(secondRow, YES_NO.getFirst()),
                dropdownColumn(secondRow, SHIPMENT_SHARES.getLast()));
    }

    /** First column of the row whose dropdown offers the given value. */
    private static OptionalInt dropdownColumn(final List<CellData> row, final String value) {
        return IntStream.range(0, row.size())
                .filter(index -> Optional.ofNullable(row.get(index))
                        .map(CellData::getDataValidation)
                        .map(DataValidationRule::getCondition)
                        .filter(condition -> "ONE_OF_LIST".equals(condition.getType()) && condition.getValues() != null)
                        .filter(condition -> condition.getValues().stream().anyMatch(item -> value.equals(item.getUserEnteredValue())))
                        .isPresent())
                .findFirst();
    }

    /**
     * Lays «Загальна акція» out from scratch: dropdowns copied into place (before their old columns are cleared or
     * deleted), columns beyond the layout deleted, data rows cleared, then header, widths, filter and frozen columns.
     */
    private static List<Request> templateRequests(final Template template, final int rows) {
        final int sheetId = template.sheetId();
        final List<Request> requests = new ArrayList<>();
        if (template.rowCount() < FIRST_DATA_ROW + rows) {
            requests.add(appendRows(sheetId, FIRST_DATA_ROW + rows - template.rowCount()));
        }
        if (template.columnCount() < WIDTH) {
            requests.add(appendColumns(sheetId, WIDTH - template.columnCount()));
        }

        for (final TotalColumn column : YES_NO_COLUMNS) {
            requests.add(dropdown(sheetId, template.yesNoColumn(), column, rows, YES_NO));
        }
        requests.add(dropdown(sheetId, template.shareColumn(), TotalColumn.FIRST_SHIPMENT, rows, SHIPMENT_SHARES));
        // Only after copying: the template's dropdown may sit in any of these columns.
        final Set<Integer> dropdownColumns = new HashSet<>();
        YES_NO_COLUMNS.forEach(column -> dropdownColumns.add(column.index()));
        dropdownColumns.add(TotalColumn.FIRST_SHIPMENT.index());
        for (int index = 0; index < WIDTH; index++) {
            final int fromRow = dropdownColumns.contains(index) ? FIRST_DATA_ROW + rows : FIRST_DATA_ROW;
            requests.add(clearValidation(openRange(sheetId, fromRow, index, index + 1)));
        }
        if (template.columnCount() > WIDTH) {
            requests.add(deleteColumns(sheetId, WIDTH, template.columnCount()));
        }

        requests.add(clear(openRange(sheetId, FIRST_DATA_ROW, 0, null)));
        requests.add(texts(sheetId, HEADER_ROW, 0, Arrays.stream(TotalColumn.values()).map(TotalColumn::getHeader).toList()));
        for (final TotalColumn column : TotalColumn.values()) {
            requests.add(headerFormat(range(sheetId, HEADER_ROW, 1, column.index(), 1),
                    column.isManagerColumn() ? MANAGER_HEADER : ANALYST_HEADER));
            requests.add(columnWidth(sheetId, column.index(), column.getWidthPx()));
        }
        requests.add(rowHeight(sheetId, HEADER_ROW, HEADER_HEIGHT_PX));
        requests.add(frozenColumns(sheetId, FROZEN_COLUMNS));
        requests.add(basicFilter(openRange(sheetId, HEADER_ROW, 0, WIDTH)));
        return requests;
    }

    /** Copies the template's dropdown into the column (keeping its chip colours), or creates a plain one. */
    private static Request dropdown(final int sheetId, final OptionalInt templateColumn, final TotalColumn column,
                                    final int rows, final List<String> values) {
        final GridRange target = range(sheetId, FIRST_DATA_ROW, rows, column.index(), 1);
        if (templateColumn.isPresent()) {
            return copyValidation(range(sheetId, FIRST_DATA_ROW, 1, templateColumn.getAsInt(), 1), target);
        }
        return listValidation(target, values);
    }

    // ---- data ---------------------------------------------------------------------------------------------------

    private static List<SheetRows> dataBlocks(final List<ReportRow> rows,
                                              final Map<String, List<ReportRow>> rowsByManager,
                                              final List<List<Object>> products) {
        final int startRow = FIRST_DATA_ROW + 1;
        final List<SheetRows> blocks = new ArrayList<>();
        blocks.add(new SheetRows(TOTAL_SHEET, startRow, dataValues(rows)));
        rowsByManager.forEach((manager, managerRows) -> blocks.add(new SheetRows(manager, startRow, dataValues(managerRows))));
        blocks.add(new SheetRows(FORECAST_SHEET, startRow, products));
        return blocks;
    }

    private static List<List<Object>> dataValues(final List<ReportRow> rows) {
        return rows.stream()
                .map(row -> TotalColumn.DATA.stream()
                        .map(column -> SheetCells.toCellValue(column.getSource().extract(row)))
                        .toList())
                .toList();
    }

    /** One «Прогноз» row (code, name) per product, in report order. */
    private static List<List<Object>> products(final List<ReportRow> rows) {
        final Map<String, String> names = new LinkedHashMap<>();
        rows.forEach(row -> names.putIfAbsent(row.productId(), row.productName()));
        return names.entrySet().stream()
                .<List<Object>>map(product -> List.of(product.getKey(), SheetCells.toCellValue(product.getValue())))
                .toList();
    }

    /** Rows of each manager, managers in Ukrainian alphabetical order; rows without a manager get no sheet. */
    private Map<String, List<ReportRow>> groupByManager(final List<ReportRow> rows) {
        final Map<String, List<ReportRow>> rowsByManager = new TreeMap<>(Collator.getInstance(Locale.forLanguageTag("uk")));
        for (final ReportRow row : rows) {
            final String manager = row.manager() == null ? "" : row.manager().strip();
            if (!manager.isEmpty() && !isPermanent(manager)) {
                rowsByManager.computeIfAbsent(manager, key -> new ArrayList<>()).add(row);
            }
        }
        return rowsByManager;
    }

    // ---- formulas & formatting ----------------------------------------------------------------------------------

    private static List<Request> formulaAndFormatRequests(final int totalSheetId,
                                                          final int forecastSheetId,
                                                          final int totalRows,
                                                          final int productRows,
                                                          final Map<String, List<ReportRow>> rowsByManager,
                                                          final Map<String, Integer> managerSheetIds,
                                                          final LayoutFormulas formulas) {
        final List<Request> requests = new ArrayList<>();

        requests.add(fillFormula(column(totalSheetId, TotalColumn.ADJUSTED_QTY, totalRows), formulas.adjustedQty()));
        requests.add(fillFormula(column(totalSheetId, TotalColumn.MANAGER_QTY, totalRows), formulas.managerQty()));
        requests.add(fillFormula(column(totalSheetId, TotalColumn.INCLUDE_STOCK, totalRows), formulas.managerFlag(TotalColumn.INCLUDE_STOCK)));
        requests.add(fillFormula(column(totalSheetId, TotalColumn.MULTIPLICITY, totalRows), formulas.multiplicity()));
        requests.add(fillFormula(column(totalSheetId, TotalColumn.INCLUDE_MULTIPLICITY, totalRows), formulas.managerFlag(TotalColumn.INCLUDE_MULTIPLICITY)));
        requests.add(fillFormula(column(totalSheetId, TotalColumn.FIRST_SHIPMENT, totalRows), formulas.firstShipment()));
        requests.addAll(columnFormats(totalSheetId, totalRows));
        requests.add(borders(range(totalSheetId, FIRST_DATA_ROW, totalRows, 0, WIDTH)));

        managerSheetIds.forEach((manager, sheetId) -> {
            final int rows = rowsByManager.get(manager).size();
            requests.add(fillFormula(column(sheetId, TotalColumn.ADJUSTED_QTY, rows), formulas.adjustedQty()));
            requests.add(fillFormula(column(sheetId, TotalColumn.MULTIPLICITY, rows), formulas.multiplicity()));
            requests.add(fillNumber(column(sheetId, TotalColumn.FIRST_SHIPMENT, rows), FIRST_SHIPMENT_SHARE));
            requests.addAll(columnFormats(sheetId, rows));
            requests.add(borders(range(sheetId, FIRST_DATA_ROW, rows, 0, WIDTH)));
        });

        requests.add(fillFormula(column(forecastSheetId, ForecastColumn.ANALYST_FORECAST, productRows), formulas.analystForecast()));
        requests.add(numberFormat(column(forecastSheetId, ForecastColumn.ANALYST_FORECAST, productRows), INTEGER_FORMAT));
        requests.add(fillFormula(column(forecastSheetId, ForecastColumn.DEVIATION, productRows), formulas.deviation()));
        requests.add(numberFormat(column(forecastSheetId, ForecastColumn.DEVIATION, productRows), DEVIATION_FORMAT));
        FORECAST_INPUT_COLUMNS.forEach(input ->
                requests.add(inputFormat(column(forecastSheetId, input, productRows), INTEGER_FORMAT, INPUT_BACKGROUND)));
        requests.add(borders(range(forecastSheetId, FIRST_DATA_ROW, productRows, 0, ForecastColumn.values().length)));
        return requests;
    }

    /** Number formats of «Загальна акція» and the manager sheets, the same as in the cyclic-action template. */
    private static List<Request> columnFormats(final int sheetId, final int rows) {
        return List.of(
                numberFormat(column(sheetId, TotalColumn.AVG_DAILY_SALES, rows), DECIMAL_FORMAT),
                numberFormat(column(sheetId, TotalColumn.FORECAST_QUANTITY, rows), INTEGER_FORMAT),
                numberFormat(column(sheetId, TotalColumn.ADJUSTED_QTY, rows), INTEGER_FORMAT),
                numberFormat(column(sheetId, TotalColumn.MANAGER_QTY, rows), INTEGER_FORMAT),
                numberFormat(column(sheetId, TotalColumn.FIRST_SHIPMENT, rows), SHARE_FORMAT));
    }

    private static GridRange column(final int sheetId, final TotalColumn column, final int rows) {
        return range(sheetId, FIRST_DATA_ROW, rows, column.index(), 1);
    }

    private static GridRange column(final int sheetId, final ForecastColumn column, final int rows) {
        return range(sheetId, FIRST_DATA_ROW, rows, column.index(), 1);
    }

    // ---- misc ---------------------------------------------------------------------------------------------------

    private boolean isPermanent(final String title) {
        return PERMANENT_SHEETS.contains(title) || title.equals(properties.sheetName());
    }

    private static SheetProperties requireSheet(final Map<String, SheetProperties> sheets, final String title) {
        final SheetProperties sheet = sheets.get(title);
        if (sheet == null) {
            throw new IllegalStateException("Template sheet «%s» not found in the spreadsheet".formatted(title));
        }
        return sheet;
    }

    private static int newSheetId(final Set<Integer> used) {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        } while (!used.add(id));
        return id;
    }

    /** Data rows of a column, e.g. {@code L2:L}. */
    private static String columnRange(final int column) {
        final String letter = letter(column);
        return letter + (FIRST_DATA_ROW + 1) + ":" + letter;
    }
}
