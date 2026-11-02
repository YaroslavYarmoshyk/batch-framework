package com.etake.googlesheets;

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

import java.io.File;
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
@RequiredArgsConstructor
public class GoogleSheetsGateway {
    /** Values are parsed as if typed by a user: formulas, ISO dates and percents become typed cells. */
    private static final String USER_ENTERED = "USER_ENTERED";
    /** Keeps each request payload well under the Sheets API size limits for large promotions. */
    static final int ROWS_PER_REQUEST = 5_000;

    private final String applicationName;
    private final File serviceAccountKey;
    private final String spreadsheetId;
    private Sheets client;

    public Spreadsheet getSpreadsheet(final String fields) {
        return execute(() -> client().spreadsheets().get(spreadsheetId).setFields(fields).execute());
    }

    /** Spreadsheet restricted to the given A1 ranges; {@code fields} may then select grid data of those ranges. */
    public Spreadsheet getSpreadsheet(final String fields, final List<String> ranges) {
        return execute(() -> client().spreadsheets().get(spreadsheetId).setRanges(ranges).setFields(fields).execute());
    }

    /** Reads ranges as raw values; dates come back as serial numbers (see {@link SheetCells#toSerialNumber}). */
    public List<ValueRange> readValues(final List<String> ranges) {
        final List<ValueRange> valueRanges = execute(() -> client().spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(ranges)
                .setValueRenderOption("UNFORMATTED_VALUE")
                .setDateTimeRenderOption("SERIAL_NUMBER")
                .execute()
                .getValueRanges());
        return Objects.requireNonNullElse(valueRanges, List.of());
    }

    /** Reads ranges as entered by users: formula cells come back as their formula text. */
    public List<ValueRange> readFormulas(final List<String> ranges) {
        final List<ValueRange> valueRanges = execute(() -> client().spreadsheets().values()
                .batchGet(spreadsheetId)
                .setRanges(ranges)
                .setValueRenderOption("FORMULA")
                .execute()
                .getValueRanges());
        return Objects.requireNonNullElse(valueRanges, List.of());
    }

    public void clearValues(final String range) {
        execute(() -> client().spreadsheets().values()
                .clear(spreadsheetId, range, new ClearValuesRequest())
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
                .batchUpdate(spreadsheetId, new BatchUpdateSpreadsheetRequest().setRequests(requests))
                .execute());
    }

    /** Writes each value range into its own A1 range, in a single request. */
    public void writeValues(final List<ValueRange> data) {
        if (!data.isEmpty()) {
            sendValues(data);
        }
    }

    private void sendValues(final List<ValueRange> data) {
        execute(() -> client().spreadsheets().values()
                .batchUpdate(spreadsheetId, new BatchUpdateValuesRequest()
                        .setValueInputOption(USER_ENTERED)
                        .setData(data))
                .execute());
    }

    private synchronized Sheets client() throws IOException, GeneralSecurityException {
        if (client == null) {
            try (FileInputStream in = new FileInputStream(serviceAccountKey)) {
                final GoogleCredentials credentials = ServiceAccountCredentials.fromStream(in)
                        .createScoped(SheetsScopes.SPREADSHEETS);
                client = new Sheets.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        GsonFactory.getDefaultInstance(),
                        new HttpCredentialsAdapter(credentials))
                        .setApplicationName(applicationName)
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
