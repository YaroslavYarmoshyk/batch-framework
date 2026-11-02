package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.AdjustmentRequest;
import com.etake.storeplanadjustment.model.InputColumn;
import com.etake.storeplanadjustment.model.InvalidAdjustmentRow;
import com.etake.storeplanadjustment.model.NamedRef;
import com.etake.storeplanadjustment.repository.LocationRepository;
import com.etake.storeplanadjustment.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Reads {@code store_plan_adjustments.xlsx} and returns one {@link AdjustmentRequest} per
 * (store, date) inside the configured date range. Rows for the same (store, date) are merged: rows
 * with no {@code amount} union their time ranges into {@code algorithmSlots}; rows that do carry an
 * {@code amount} skip the algorithm entirely and have their amounts summed instead. A store whose
 * every row is outside the range is never resolved or queried, so it never appears in the report.
 *
 * <p>Store names are resolved to {@code locations.id} here, so an unknown store is reported with the
 * rest. Any invalid row in the range stops the run: all of them are written to a report (see
 * {@link InvalidRowsReportService}) and an exception fails the job, so a plan is never adjusted from
 * a partly read file. A row with no readable date can't be placed outside the range, so it counts as
 * invalid too; a completely empty row is ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentFileService {
    // Strict, so an impossible date like 31.02.2026 is rejected rather than moved to 28.02.2026.
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private final SystemConfigurationProperties properties;
    private final TimeSlotParser timeSlotParser;
    private final InvalidRowsReportService invalidRowsReportService;
    private final LocationRepository locationRepository;
    private final DataFormatter cellFormatter = new DataFormatter();

    public List<AdjustmentRequest> readAdjustments() {
        final Map<InputColumn, Integer> columns = properties.inputColumns();
        final int storeColumn = columns.get(InputColumn.STORE);
        final int dateColumn = columns.get(InputColumn.DATE);
        final int timeRangesColumn = columns.get(InputColumn.TIME_RANGES);
        final Integer amountColumn = columns.get(InputColumn.AMOUNT);
        final LocalDate fromDate = properties.dateRange().fromDate();
        final LocalDate toDate = properties.dateRange().toDate();

        final List<ParsedRow> rows = new ArrayList<>();
        int outOfRangeRows = 0;

        try (InputStream in = new FileInputStream(ResourcePaths.resolve(properties.resources().input()));
             Workbook workbook = WorkbookFactory.create(in)) {

            final Sheet sheet = workbook.getSheetAt(0);
            final int firstDataRow = sheet.getFirstRowNum() + 1;
            final int lastRow = sheet.getLastRowNum();

            for (int r = firstDataRow; r <= lastRow; r++) {
                final Row row = sheet.getRow(r);
                if (row == null || isEmptyRow(row, columns)) {
                    continue;
                }

                final List<String> errors = new ArrayList<>();
                final LocalDate date = readDate(row.getCell(dateColumn), errors);

                // Dropped before any validation, so errors outside this run's range don't stop it.
                if (date != null && !isWithinRange(date, fromDate, toDate)) {
                    outOfRangeRows++;
                    continue;
                }

                final String storeName = readString(row.getCell(storeColumn));
                if (storeName.isBlank()) {
                    errors.add("Не вказано магазин");
                }
                final Set<LocalTime> slots = readSlots(readString(row.getCell(timeRangesColumn)), errors);
                final Cell amountCell = amountColumn == null ? null : row.getCell(amountColumn);
                final BigDecimal amount = readAmount(amountCell, errors);

                rows.add(new ParsedRow(
                        r + 1, storeName, date, slots, amount, errors,
                        display(row.getCell(storeColumn)),
                        date != null ? date.format(DATE_FORMAT) : display(row.getCell(dateColumn)),
                        display(row.getCell(timeRangesColumn)),
                        display(amountCell)));
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to read adjustments file: "
                    + ResourcePaths.resolve(properties.resources().input()).getAbsolutePath(), e);
        }

        if (outOfRangeRows > 0) {
            log.info("Ignoring {} row(s) outside [{} .. {}]", outOfRangeRows, fromDate, toDate);
        }

        final Map<String, String> storeNameToId = resolveStores(rows, fromDate);
        for (final ParsedRow row : rows) {
            if (!row.storeName().isBlank() && !storeNameToId.containsKey(row.storeName())) {
                row.errors().addFirst("Магазин '%s' не знайдено серед відкритих на %s: перевірте назву"
                        .formatted(row.storeName(), fromDate.format(DATE_FORMAT)));
            }
        }

        final List<InvalidAdjustmentRow> invalidRows = rows.stream()
                .filter(row -> !row.errors().isEmpty())
                .map(ParsedRow::toInvalidRow)
                .toList();
        if (!invalidRows.isEmpty()) {
            final File report = invalidRowsReportService.write(invalidRows);
            throw new IllegalStateException("Adjustments file has %d invalid row(s) - fix them and run again. Report: %s"
                    .formatted(invalidRows.size(), report.getAbsolutePath()));
        }
        invalidRowsReportService.deletePrevious();

        // Keyed by store+date so repeated rows merge; LinkedHashMap keeps the file order.
        final Map<StoreDate, Accumulator> merged = new LinkedHashMap<>();
        for (final ParsedRow row : rows) {
            final Accumulator accumulator = merged.computeIfAbsent(new StoreDate(row.storeName(), row.date()), _ -> new Accumulator());
            if (row.amount() != null) {
                accumulator.amount = accumulator.amount == null ? row.amount() : accumulator.amount.add(row.amount());
            } else {
                accumulator.algorithmSlots.addAll(row.slots());
            }
        }

        final List<AdjustmentRequest> requests = merged.entrySet().stream()
                .map(e -> new AdjustmentRequest(e.getKey().storeName(), storeNameToId.get(e.getKey().storeName()),
                        e.getKey().date(), e.getValue().algorithmSlots, e.getValue().amount))
                .toList();
        log.info("Read {} store/date adjustment(s) from {}", requests.size(), properties.resources().input());
        return requests;
    }

    private Map<String, String> resolveStores(final List<ParsedRow> rows, final LocalDate startDate) {
        final Set<String> storeNames = rows.stream()
                .map(ParsedRow::storeName)
                .filter(name -> !name.isBlank())
                .collect(Collectors.toSet());
        final Map<String, String> nameToId = new HashMap<>();
        for (final NamedRef store : locationRepository.findStoresByName(storeNames, startDate)) {
            if (nameToId.putIfAbsent(store.name(), store.id()) != null) {
                log.warn("Store name '{}' resolves to multiple ids; keeping the first ({})", store.name(), nameToId.get(store.name()));
            }
        }
        return nameToId;
    }

    private static boolean isWithinRange(final LocalDate date, final LocalDate fromDate, final LocalDate toDate) {
        return !date.isBefore(fromDate) && !date.isAfter(toDate);
    }

    private boolean isEmptyRow(final Row row, final Map<InputColumn, Integer> columns) {
        return columns.values().stream().allMatch(column -> display(row.getCell(column)).isBlank());
    }

    /** The cell's text as the user sees it in Excel; empty for a missing cell. */
    private String display(final Cell cell) {
        return cell == null ? "" : cellFormatter.formatCellValue(cell).trim();
    }

    private static String readString(final Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    private LocalDate readDate(final Cell cell, final List<String> errors) {
        final String text = display(cell);
        if (text.isBlank()) {
            errors.add("Не вказано дату");
            return null;
        }
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            return LocalDate.parse(readString(cell), DATE_FORMAT);
        } catch (final RuntimeException e) {
            errors.add("Некоректна дата '%s': очікується формат ДД.ММ.РРРР".formatted(text));
            return null;
        }
    }

    private Set<LocalTime> readSlots(final String timeRanges, final List<String> errors) {
        try {
            final Set<LocalTime> slots = timeSlotParser.parse(timeRanges);
            if (slots.isEmpty()) {
                errors.add("Не вказано часові проміжки");
            }
            return slots;
        } catch (final IllegalArgumentException e) {
            errors.add(e.getMessage());
            return Set.of();
        }
    }

    private BigDecimal readAmount(final Cell cell, final List<String> errors) {
        final String text = display(cell);
        if (text.isBlank()) {
            return null;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> new BigDecimal(text);
                default -> throw new NumberFormatException();
            };
        } catch (final NumberFormatException e) {
            errors.add("Некоректна сума '%s': очікується число".formatted(text));
            return null;
        }
    }

    private record ParsedRow(int rowNumber, String storeName, LocalDate date, Set<LocalTime> slots, BigDecimal amount,
                             List<String> errors, String storeText, String dateText, String timeRangesText, String amountText) {

        private InvalidAdjustmentRow toInvalidRow() {
            return new InvalidAdjustmentRow(rowNumber, storeText, dateText, timeRangesText, amountText, String.join("; ", errors));
        }
    }

    private record StoreDate(String storeName, LocalDate date) {
    }

    private static final class Accumulator {
        private final Set<LocalTime> algorithmSlots = new TreeSet<>();
        private BigDecimal amount;
    }
}
