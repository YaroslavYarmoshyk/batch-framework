package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.Resources;
import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.AdjustmentRequest;
import com.etake.storeplanadjustment.model.InputColumn;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjustmentFileServiceTest {

    @TempDir
    Path tempDir;

    private final TimeSlotParser timeSlotParser = new TimeSlotParser();

    @Test
    void amountRowAndAlgorithmRowForSameStoreDateMergeIntoOneRequest() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00", null),
                row("19:00-20:00", "300"));

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertEquals(1, requests.size());
        final AdjustmentRequest request = requests.getFirst();
        assertEquals("Store A", request.storeName());
        assertEquals(slots(LocalTime.of(10, 0), LocalTime.of(10, 30)), request.algorithmSlots());
        assertEquals(0, request.amount().compareTo(new BigDecimal("300")));
    }

    @Test
    void multipleAmountRowsForSameStoreDateSum() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00", "100"),
                row("19:00-20:00", "200"));

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertEquals(1, requests.size());
        final AdjustmentRequest request = requests.getFirst();
        assertTrue(request.algorithmSlots().isEmpty());
        assertEquals(0, request.amount().compareTo(new BigDecimal("300")));
    }

    @Test
    void blankAmountCellBehavesLikeNoAmountColumn() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00", null));

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertEquals(1, requests.size());
        final AdjustmentRequest request = requests.getFirst();
        assertNull(request.amount());
        assertEquals(slots(LocalTime.of(10, 0), LocalTime.of(10, 30)), request.algorithmSlots());
    }

    @Test
    void unparseableAmountCellFallsBackToAlgorithmSlotsInsteadOfDroppingTheRow() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00", "not-a-number"));

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertEquals(1, requests.size());
        final AdjustmentRequest request = requests.getFirst();
        assertNull(request.amount());
        assertEquals(slots(LocalTime.of(10, 0), LocalTime.of(10, 30)), request.algorithmSlots());
    }

    private List<AdjustmentRequest> readAdjustments(final File file) {
        final SystemConfigurationProperties properties = new SystemConfigurationProperties(
                null,
                new Resources(file.getAbsolutePath(), null),
                Map.of(InputColumn.STORE, 0, InputColumn.DATE, 1, InputColumn.TIME_RANGES, 2, InputColumn.AMOUNT, 3));
        return new AdjustmentFileService(properties, timeSlotParser).readAdjustments();
    }

    private File writeWorkbook(final String[]... rows) throws IOException {
        final File file = tempDir.resolve("adjustments.xlsx").toFile();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = new FileOutputStream(file)) {
            final Sheet sheet = workbook.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            int rowIndex = 1;
            for (final String[] values : rows) {
                final Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < values.length; col++) {
                    if (values[col] != null) {
                        row.createCell(col).setCellValue(values[col]);
                    }
                }
            }
            workbook.write(out);
        }
        return file;
    }

    private static String[] row(final String timeRanges, final String amount) {
        return new String[]{"Store A", "10.06.2026", timeRanges, amount};
    }

    private static java.util.Set<LocalTime> slots(final LocalTime... times) {
        return java.util.Set.of(times);
    }
}
