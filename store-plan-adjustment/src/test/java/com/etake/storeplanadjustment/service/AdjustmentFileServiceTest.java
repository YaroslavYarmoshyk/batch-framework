package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.DateRange;
import com.etake.storeplanadjustment.config.properties.Resources;
import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.AdjustmentRequest;
import com.etake.storeplanadjustment.model.InputColumn;
import com.etake.storeplanadjustment.model.InvalidRowColumn;
import com.etake.storeplanadjustment.model.NamedRef;
import com.etake.storeplanadjustment.repository.LocationRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Collection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjustmentFileServiceTest {
    private static final String UNKNOWN_STORE = "Unknown Store";

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
    void keepsOnlyRowsInsideTheDateRangeIncludingBoundaries() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "31.05.2026", "10:00-11:00"),
                row("Store B", "01.06.2026", "10:00-11:00"),
                row("Store C", "30.06.2026", "10:00-11:00"),
                row("Store D", "01.07.2026", "10:00-11:00"));

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertEquals(List.of("Store B", "Store C"), requests.stream().map(AdjustmentRequest::storeName).toList());
    }

    @Test
    void invalidRowOutsideTheDateRangeDoesNotStopTheRun() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "31.05.2026", "garbage"),
                row("", "01.07.2026", "10:00-11:00"));

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertTrue(requests.isEmpty());
        assertFalse(Files.exists(reportPath()));
    }

    @Test
    void emptyRowIsIgnored() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "10.06.2026", "10:00-11:00"),
                new String[]{"", "  ", null, null});

        final List<AdjustmentRequest> requests = readAdjustments(file);

        assertEquals(1, requests.size());
        assertFalse(Files.exists(reportPath()));
    }

    @Test
    void invalidRowInsideTheDateRangeStopsTheRunAndIsReported() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "10.06.2026", "10:00-11:00"),
                row("Store B", "11.06.2026", "garbage"));

        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        assertTrue(e.getMessage().contains(reportPath().toString()));
        final List<List<String>> report = readReport();
        assertEquals(1, report.size());
        assertEquals(List.of("3", "Store B", "11.06.2026", "garbage", ""), report.getFirst().subList(0, 5));
        assertTrue(reason(report.getFirst()).contains("Некоректний часовий проміжок 'garbage'"));
    }

    @Test
    void everyProblemInARowIsInTheReason() throws IOException {
        final File file = writeWorkbook(
                new String[]{"", "10.06.2026", "", "abc"});

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        final String reason = reason(readReport().getFirst());
        assertTrue(reason.contains("Не вказано магазин"));
        assertTrue(reason.contains("Не вказано часові проміжки"));
        assertTrue(reason.contains("Некоректна сума 'abc'"));
    }

    @Test
    void rowWithAPartlyInvalidTimeRangeCellIsReported() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00;garbage", null));

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        assertTrue(reason(readReport().getFirst()).contains("'garbage'"));
    }

    @Test
    void unparseableAmountIsReportedInsteadOfFallingBackToTheAlgorithm() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00", "not-a-number"));

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        assertTrue(reason(readReport().getFirst()).contains("Некоректна сума 'not-a-number'"));
    }

    @Test
    void rowWithUnreadableDateIsReportedBecauseItsMonthIsUnknown() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "not-a-date", "10:00-11:00"),
                row("Store B", "", "10:00-11:00"));

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        final List<List<String>> report = readReport();
        assertTrue(reason(report.get(0)).contains("Некоректна дата 'not-a-date'"));
        assertTrue(reason(report.get(1)).contains("Не вказано дату"));
    }

    @Test
    void impossibleCalendarDateIsReported() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "31.06.2026", "10:00-11:00"));

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        assertTrue(reason(readReport().getFirst()).contains("Некоректна дата '31.06.2026'"));
    }

    @Test
    void knownStoreIsResolvedToItsId() throws IOException {
        final File file = writeWorkbook(
                row("10:00-11:00", null));

        assertEquals("id-Store A", readAdjustments(file).getFirst().storeId());
    }

    @Test
    void unknownStoreStopsTheRunAndIsReported() throws IOException {
        final File file = writeWorkbook(
                row("Store A", "10.06.2026", "10:00-11:00"),
                row(UNKNOWN_STORE, "11.06.2026", "10:00-11:00"));

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        final List<List<String>> report = readReport();
        assertEquals(1, report.size());
        assertEquals("3", report.getFirst().getFirst());
        assertTrue(reason(report.getFirst()).contains("Магазин '" + UNKNOWN_STORE + "' не знайдено серед відкритих на 01.06.2026"));
    }

    @Test
    void unknownStoreIsReportedTogetherWithTheRowsOtherProblems() throws IOException {
        final File file = writeWorkbook(
                row(UNKNOWN_STORE, "10.06.2026", "garbage"));

        assertThrows(IllegalStateException.class, () -> readAdjustments(file));

        final String reason = reason(readReport().getFirst());
        assertTrue(reason.contains("Магазин '" + UNKNOWN_STORE + "'"));
        assertTrue(reason.contains("'garbage'"));
    }

    @Test
    void unknownStoreOutsideTheDateRangeDoesNotStopTheRun() throws IOException {
        final File file = writeWorkbook(
                row(UNKNOWN_STORE, "10.05.2026", "10:00-11:00"));

        assertTrue(readAdjustments(file).isEmpty());
        assertFalse(Files.exists(reportPath()));
    }

    @Test
    void cleanRunDeletesTheReportOfAnEarlierFailedRun() throws IOException {
        Files.writeString(reportPath(), "stale");
        final File file = writeWorkbook(
                row("10:00-11:00", null));

        readAdjustments(file);

        assertFalse(Files.exists(reportPath()));
    }

    private List<AdjustmentRequest> readAdjustments(final File file) {
        final SystemConfigurationProperties properties = new SystemConfigurationProperties(
                new DateRange(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)),
                new Resources(file.getAbsolutePath(), null, reportPath().toString()),
                Map.of(InputColumn.STORE, 0, InputColumn.DATE, 1, InputColumn.TIME_RANGES, 2, InputColumn.AMOUNT, 3));
        return new AdjustmentFileService(properties, timeSlotParser, new InvalidRowsReportService(properties), new FakeLocationRepository())
                .readAdjustments();
    }

    /** Every store is open except {@link #UNKNOWN_STORE}. */
    private static final class FakeLocationRepository extends LocationRepository {
        private FakeLocationRepository() {
            super(null);
        }

        @Override
        public List<NamedRef> findStoresByName(final Collection<String> storeNames, final LocalDate startDate) {
            return storeNames.stream()
                    .filter(name -> !name.equals(UNKNOWN_STORE))
                    .map(name -> new NamedRef("id-" + name, name))
                    .toList();
        }
    }

    private Path reportPath() {
        return tempDir.resolve("invalid-adjustments.xlsx");
    }

    /** The report's data rows as text, in column order, with the row number first. */
    private List<List<String>> readReport() throws IOException {
        try (Workbook workbook = WorkbookFactory.create(reportPath().toFile())) {
            final Sheet sheet = workbook.getSheetAt(0);
            final List<List<String>> rows = new java.util.ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                final Row row = sheet.getRow(r);
                final List<String> values = new java.util.ArrayList<>();
                values.add(String.valueOf((int) row.getCell(InvalidRowColumn.ROW_NUMBER.getIndex()).getNumericCellValue()));
                for (int col = InvalidRowColumn.STORE.getIndex(); col <= InvalidRowColumn.REASON.getIndex(); col++) {
                    values.add(row.getCell(col).getStringCellValue());
                }
                rows.add(values);
            }
            return rows;
        }
    }

    private static String reason(final List<String> reportRow) {
        return reportRow.get(InvalidRowColumn.REASON.getIndex());
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

    private static String[] row(final String store, final String date, final String timeRanges) {
        return new String[]{store, date, timeRanges, null};
    }

    private static java.util.Set<LocalTime> slots(final LocalTime... times) {
        return java.util.Set.of(times);
    }
}
