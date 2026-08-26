package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.AdjustmentRequest;
import com.etake.storeplanadjustment.model.InputColumn;
import com.etake.storeplanadjustment.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads {@code store_plan_adjustments.xlsx} and returns one {@link AdjustmentRequest} per
 * (store, date), with the time ranges of all matching rows unioned into a single slot set.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustmentFileService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SystemConfigurationProperties properties;
    private final TimeSlotParser timeSlotParser;

    public List<AdjustmentRequest> readAdjustments() {
        final Map<InputColumn, Integer> columns = properties.inputColumns();
        final int storeColumn = columns.get(InputColumn.STORE);
        final int dateColumn = columns.get(InputColumn.DATE);
        final int timeRangesColumn = columns.get(InputColumn.TIME_RANGES);

        // Keyed by store+date so repeated rows merge; LinkedHashMap keeps the file order.
        final Map<StoreDate, Set<LocalTime>> merged = new LinkedHashMap<>();

        try (InputStream in = new FileInputStream(ResourcePaths.resolve(properties.resources().input()));
             Workbook workbook = WorkbookFactory.create(in)) {

            final Sheet sheet = workbook.getSheetAt(0);
            final int firstDataRow = sheet.getFirstRowNum() + 1;
            final int lastRow = sheet.getLastRowNum();

            for (int r = firstDataRow; r <= lastRow; r++) {
                final Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                final String storeName = readString(row.getCell(storeColumn));
                final LocalDate date = readDate(row.getCell(dateColumn), r);
                final String timeRanges = readString(row.getCell(timeRangesColumn));

                if (storeName.isBlank() || date == null) {
                    log.warn("Skipping row {}: missing store or date", r + 1);
                    continue;
                }

                final Set<LocalTime> slots = timeSlotParser.parse(timeRanges);
                if (slots.isEmpty()) {
                    log.warn("Skipping row {} for store {} at {}: no usable time ranges in '{}'", r + 1, storeName, date, timeRanges);
                    continue;
                }

                merged.computeIfAbsent(new StoreDate(storeName, date), k -> new TreeSet<>())
                        .addAll(slots);
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to read adjustments file: "
                    + ResourcePaths.resolve(properties.resources().input()).getAbsolutePath(), e);
        }

        final List<AdjustmentRequest> requests = merged.entrySet().stream()
                .map(e -> new AdjustmentRequest(e.getKey().storeName(), e.getKey().date(), e.getValue()))
                .toList();
        log.info("Read {} store/date adjustment(s) from {}", requests.size(), properties.resources().input());
        return requests;
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

    private LocalDate readDate(final Cell cell, final int rowIndex) {
        if (cell == null) {
            return null;
        }
        try {
            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }
            return LocalDate.parse(readString(cell), DATE_FORMAT);
        } catch (final RuntimeException e) {
            log.warn("Skipping row {}: unparseable date", rowIndex + 1);
            return null;
        }
    }

    private record StoreDate(String storeName, LocalDate date) {
    }
}
