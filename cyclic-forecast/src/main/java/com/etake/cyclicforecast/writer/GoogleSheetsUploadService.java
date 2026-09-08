package com.etake.cyclicforecast.writer;

import com.etake.cyclicforecast.config.properties.CyclicForecastProperties;
import com.etake.cyclicforecast.model.ReportRow;
import com.etake.cyclicforecast.util.Constants;
import com.etake.cyclicforecast.util.ResourcePaths;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSheetsUploadService {
    private final CyclicForecastProperties properties;

    public void upload(final List<ReportRow> rows) {
        final var config = properties.googleSheets();
        if (!config.enabled() || isBlank(config.serviceAccountKeyPath()) || isBlank(config.spreadsheetId())) {
            log.info("Google Sheets upload skipped (disabled or not configured)");
            return;
        }

        try {
            final Sheets sheets = buildClient(config.serviceAccountKeyPath());
            final ValueRange body = new ValueRange().setValues(toValues(rows));
            sheets.spreadsheets().values()
                    .update(config.spreadsheetId(), config.sheetName() + "!A1", body)
                    .setValueInputOption("RAW")
                    .execute();
            log.info("Uploaded {} rows to Google Sheet {}", rows.size(), config.spreadsheetId());
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to upload forecast to Google Sheets", e);
        }
    }

    private Sheets buildClient(final String serviceAccountKeyPath) throws Exception {
        final File keyFile = ResourcePaths.resolve(serviceAccountKeyPath);
        try (FileInputStream in = new FileInputStream(keyFile)) {
            final GoogleCredentials credentials = ServiceAccountCredentials.fromStream(in)
                    .createScoped(SheetsScopes.SPREADSHEETS);
            return new Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credentials))
                    .setApplicationName("cyclic-forecast")
                    .build();
        }
    }

    private static List<List<Object>> toValues(final List<ReportRow> rows) {
        final List<List<Object>> values = new ArrayList<>();
        values.add(List.of((Object[]) Constants.HEADERS));
        for (final ReportRow row : rows) {
            values.add(rowValues(row));
        }
        return values;
    }

    private static List<Object> rowValues(final ReportRow row) {
        final List<Object> values = new ArrayList<>(Constants.COLUMN_COUNT);
        values.add(nullToEmpty(row.manager()));
        values.add(nullToEmpty(row.store()));
        values.add(nullToEmpty(row.storeFormat()));
        values.add(nullToEmpty(row.productId()));
        values.add(nullToEmpty(row.productName()));
        values.add(nullToEmpty(row.subcategory()));
        values.add(nullToEmpty(row.promotionType()));
        values.add(row.carryover() != null ? row.carryover() : "");
        values.add(nullToEmpty(row.algorithmLevel()));
        values.add(nullToEmpty(row.donorKey()));
        values.add(row.avgDailySales() != null ? row.avgDailySales() : "");
        values.add(row.forecastQuantity() != null ? row.forecastQuantity() : "");
        return values;
    }

    private static Object nullToEmpty(final String value) {
        return value != null ? value : "";
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }
}
