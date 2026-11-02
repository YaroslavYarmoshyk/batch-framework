package com.etake.promotionforecast.writer.googlesheets;

import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import com.etake.promotionforecast.util.ResourcePaths;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.ClearValuesRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Thin wrapper over the Sheets API bound to the configured spreadsheet. The client is created lazily,
 * so nothing touches the key file while the export is disabled.
 */
@Component
@RequiredArgsConstructor
public class GoogleSheetsGateway {
    private static final String APPLICATION_NAME = "promotion-forecast";
    /** Values are parsed as if typed by a user: formulas, ISO dates and percents become typed cells. */
    private static final String USER_ENTERED = "USER_ENTERED";
    /** Keeps each request payload well under the Sheets API size limits for large promotions. */
    static final int ROWS_PER_REQUEST = 5_000;

    private final GoogleSheetsProperties properties;
    private Sheets client;

    public Spreadsheet getSpreadsheet(final String fields) {
        return execute(() -> client().spreadsheets().get(spreadsheetId()).setFields(fields).execute());
    }

    /** Spreadsheet restricted to the given A1 ranges; {@code fields} may then select grid data of those ranges. */
    public Spreadsheet getSpreadsheet(final String fields, final List<String> ranges) {
        return execute(() -> client().spreadsheets().get(spreadsheetId()).setRanges(ranges).setFields(fields).execute());
    }

    /** Reads ranges as raw values; dates come back as serial numbers (see {@link SheetCells#toSerialNumber}). */
    public List<ValueRange> readValues(final List<String> ranges) {
        final List<ValueRange> valueRanges = execute(() -> client().spreadsheets().values()
                .batchGet(spreadsheetId())
                .setRanges(ranges)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .setDateTimeRenderOption("SERIAL_NUMBER")
                .execute()
                .getValueRanges());
        return Objects.requireNonNullElse(valueRanges, List.of());
    }

    public void clearValues(final String range) {
        execute(() -> client().spreadsheets().values()
                .clear(spreadsheetId(), range, new ClearValuesRequest())
                .execute());
    }

    /** Writes the blocks with as few requests as possible, never more than {@link #ROWS_PER_REQUEST} rows each. */
    public void writeRows(final List<SheetRows> blocks) {
        List<ValueRange> batch = new ArrayList<>();
        int batchRows = 0;
        for (final SheetRows block : blocks) {
            for (int from = 0; from < block.rows().size(); from += ROWS_PER_REQUEST) {
                final List<List<Object>> chunk = block.rows().subList(from, Math.min(from + ROWS_PER_REQUEST, block.rows().size()));
                if (batchRows + chunk.size() > ROWS_PER_REQUEST && !batch.isEmpty()) {
                    sendValues(batch);
                    batch = new ArrayList<>();
                    batchRows = 0;
                }
                batch.add(new ValueRange()
                        .setRange(SheetCells.a1(block.sheetName(), "A" + (block.startRow() + from)))
                        .setValues(chunk));
                batchRows += chunk.size();
            }
        }
        if (!batch.isEmpty()) {
            sendValues(batch);
        }
    }

    /** Applies the requests atomically and in order: later requests may refer to sheets created by earlier ones. */
    public void batchUpdate(final List<Request> requests) {
        if (requests.isEmpty()) {
            return;
        }
        execute(() -> client().spreadsheets()
                .batchUpdate(spreadsheetId(), new BatchUpdateSpreadsheetRequest().setRequests(requests))
                .execute());
    }

    private void sendValues(final List<ValueRange> data) {
        execute(() -> client().spreadsheets().values()
                .batchUpdate(spreadsheetId(), new BatchUpdateValuesRequest()
                        .setValueInputOption(USER_ENTERED)
                        .setData(data))
                .execute());
    }

    private String spreadsheetId() {
        return properties.spreadsheetId();
    }

    private synchronized Sheets client() throws IOException, GeneralSecurityException {
        if (client == null) {
            try (FileInputStream in = new FileInputStream(ResourcePaths.resolve(properties.serviceAccountKeyPath()))) {
                final GoogleCredentials credentials = ServiceAccountCredentials.fromStream(in)
                        .createScoped(SheetsScopes.SPREADSHEETS);
                client = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credentials))
                        .setApplicationName(APPLICATION_NAME)
                        .build();
            }
        }
        return client;
    }

    private static <T> T execute(final SheetsCall<T> call) {
        try {
            return call.execute();
        } catch (final IOException e) {
            throw new UncheckedIOException("Google Sheets request failed", e);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Cannot initialise Google Sheets transport", e);
        }
    }

    @FunctionalInterface
    private interface SheetsCall<T> {
        T execute() throws IOException, GeneralSecurityException;
    }
}
