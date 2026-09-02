package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.config.SystemConfigurationProperties;
import com.etake.avgcheckplan.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads {@code avg_items_per_check_plan.xlsx} (columns: {@code stores}, {@code avg_items_per_check_plan}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvgItemsPerCheckFileService {
    private static final int STORE_COLUMN = 0;
    private static final int AVG_ITEMS_PER_CHECK_COLUMN = 1;

    private final SystemConfigurationProperties properties;

    public Map<String, BigDecimal> readAvgItemsPerCheck() {
        final Map<String, BigDecimal> avgItemsPerCheckByStore = new LinkedHashMap<>();

        try (InputStream in = new FileInputStream(ResourcePaths.resolve(properties.input()));
             Workbook workbook = WorkbookFactory.create(in)) {

            final Sheet sheet = workbook.getSheetAt(0);
            final int firstDataRow = sheet.getFirstRowNum() + 1;
            final int lastRow = sheet.getLastRowNum();

            for (int r = firstDataRow; r <= lastRow; r++) {
                final Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                final String storeName = readString(row.getCell(STORE_COLUMN));
                final BigDecimal avgItemsPerCheck = readDecimal(row.getCell(AVG_ITEMS_PER_CHECK_COLUMN), r);

                if (storeName.isBlank() || avgItemsPerCheck == null) {
                    log.warn("Skipping row {}: missing store or avg items per check", r + 1);
                    continue;
                }

                avgItemsPerCheckByStore.put(storeName, avgItemsPerCheck);
            }
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to read avg items per check file: "
                    + ResourcePaths.resolve(properties.input()).getAbsolutePath(), e);
        }

        log.info("Read {} store avg items per check value(s) from {}", avgItemsPerCheckByStore.size(), properties.input());
        return avgItemsPerCheckByStore;
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

    private BigDecimal readDecimal(final Cell cell, final int rowIndex) {
        if (cell == null) {
            return null;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING -> {
                    final String text = cell.getStringCellValue().trim();
                    yield text.isBlank() ? null : new BigDecimal(text);
                }
                default -> null;
            };
        } catch (final NumberFormatException e) {
            log.warn("Row {}: unparseable avg items per check value '{}'", rowIndex + 1, readString(cell));
            return null;
        }
    }
}
