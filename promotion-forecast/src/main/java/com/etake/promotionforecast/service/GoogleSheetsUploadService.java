package com.etake.promotionforecast.service;

import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import com.etake.promotionforecast.model.ReportColumn;
import com.etake.promotionforecast.model.ReportRow;
import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.googlesheets.SheetCells;
import com.etake.googlesheets.SheetRequests;
import com.etake.googlesheets.SheetRows;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Uploads the full report (all columns, including donor details) into the source sheet ({@code result}). */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSheetsUploadService {
    private static final String SHEET_FIELDS = "sheets.properties(sheetId,title,gridProperties(rowCount,columnCount))";
    /** Grid of a sheet created through the UI. */
    private static final int DEFAULT_ROWS = 1_000;
    private static final int DEFAULT_COLUMNS = 26;
    private final GoogleSheetsGateway gateway;
    private final GoogleSheetsProperties properties;

    public void upload(final List<ReportRow> rows) {
        if (!properties.isConfigured()) {
            log.info("Google Sheets upload skipped (disabled or not configured)");
            return;
        }

        final String sheetName = properties.sheetName();
        final List<List<Object>> values = toValues(rows);
        prepareSheet(sheetName, values.size(), ReportColumn.values().length);
        // Clear first so rows from a previous, longer run don't linger below the new data.
        gateway.clearValues(SheetCells.quote(sheetName));
        gateway.writeRows(List.of(new SheetRows(sheetName, 1, values)));
        log.info("Uploaded {} rows to Google Sheet {} ({})", rows.size(), properties.spreadsheetId(), sheetName);
    }

    /** Creates the sheet if missing and grows its grid to fit the report: values writes never grow it themselves. */
    private void prepareSheet(final String sheetName, final int rows, final int columns) {
        final Optional<SheetProperties> existing = gateway.getSpreadsheet(SHEET_FIELDS).getSheets().stream()
                .map(Sheet::getProperties)
                .filter(sheet -> sheetName.equals(sheet.getTitle()))
                .findFirst();
        if (existing.isEmpty()) {
            final GridProperties grid = new GridProperties()
                    .setRowCount(Math.max(rows, DEFAULT_ROWS))
                    .setColumnCount(Math.max(columns, DEFAULT_COLUMNS));
            gateway.batchUpdate(List.of(new Request().setAddSheet(new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName).setGridProperties(grid)))));
            log.info("Created sheet '{}' in Google Sheet {}", sheetName, properties.spreadsheetId());
            return;
        }
        final SheetProperties sheet = existing.get();
        gateway.batchUpdate(SheetRequests.growGrid(sheet.getSheetId(), sheet.getGridProperties(), rows, columns));
    }

    private static List<List<Object>> toValues(final List<ReportRow> rows) {
        final List<List<Object>> values = new ArrayList<>(rows.size() + 1);
        values.add(Arrays.stream(ReportColumn.values()).<Object>map(ReportColumn::getHeader).toList());
        for (final ReportRow row : rows) {
            values.add(Arrays.stream(ReportColumn.values())
                    .map(column -> SheetCells.toCellValue(column.extract(row)))
                    .toList());
        }
        return values;
    }
}
