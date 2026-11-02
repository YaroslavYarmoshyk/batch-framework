package com.etake.promotionforecast.service;

import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import com.etake.promotionforecast.model.ReportColumn;
import com.etake.promotionforecast.model.ReportRow;
import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.googlesheets.SheetCells;
import com.etake.googlesheets.SheetRows;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Uploads the full report (all columns, including donor details) into the source sheet ({@code result}). */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSheetsUploadService {
    private final GoogleSheetsGateway gateway;
    private final GoogleSheetsProperties properties;

    public void upload(final List<ReportRow> rows) {
        if (!properties.isConfigured()) {
            log.info("Google Sheets upload skipped (disabled or not configured)");
            return;
        }

        final String sheetName = properties.sheetName();
        ensureSheetExists(sheetName);
        // Clear first so rows from a previous, longer run don't linger below the new data.
        gateway.clearValues(SheetCells.quote(sheetName));
        gateway.writeRows(List.of(new SheetRows(sheetName, 1, toValues(rows))));
        log.info("Uploaded {} rows to Google Sheet {} ({})", rows.size(), properties.spreadsheetId(), sheetName);
    }

    private void ensureSheetExists(final String sheetName) {
        final boolean exists = gateway.getSpreadsheet("sheets.properties.title").getSheets().stream()
                .anyMatch(sheet -> sheetName.equals(sheet.getProperties().getTitle()));
        if (!exists) {
            gateway.batchUpdate(List.of(new Request().setAddSheet(new AddSheetRequest()
                    .setProperties(new SheetProperties().setTitle(sheetName)))));
            log.info("Created sheet '{}' in Google Sheet {}", sheetName, properties.spreadsheetId());
        }
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
