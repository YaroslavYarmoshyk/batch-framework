package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.InvalidAdjustmentRow;
import com.etake.storeplanadjustment.model.InvalidRowColumn;
import com.etake.storeplanadjustment.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

import static com.etake.storeplanadjustment.utils.Constants.INVALID_ROWS_SHEET;

/**
 * Writes the invalid rows of the input file, each with the reason in Ukrainian, so
 * whoever maintains the file can fix them and run the job again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvalidRowsReportService {
    private static final int EXCEL_WIDTH_UNITS_PER_CHAR = 256;

    private final SystemConfigurationProperties properties;

    public File write(final List<InvalidAdjustmentRow> rows) {
        final File file = reportFile();
        final File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Workbook workbook = new XSSFWorkbook();
             OutputStream out = new FileOutputStream(file)) {
            fill(workbook, rows);
            workbook.write(out);
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to write invalid rows report: " + file.getAbsolutePath(), e);
        }
        log.error("Wrote {} invalid input row(s) to {}", rows.size(), file.getAbsolutePath());
        return file;
    }

    /** Removes the report left by an earlier failed run, so it can't be mistaken for a current one. */
    public void deletePrevious() {
        final File file = reportFile();
        try {
            if (Files.deleteIfExists(file.toPath())) {
                log.info("Deleted the previous invalid rows report {}", file.getAbsolutePath());
            }
        } catch (final IOException e) {
            log.warn("Could not delete the previous invalid rows report {}", file.getAbsolutePath(), e);
        }
    }

    private File reportFile() {
        return ResourcePaths.resolve(properties.resources().invalidRows());
    }

    private static void fill(final Workbook workbook, final List<InvalidAdjustmentRow> rows) {
        final Sheet sheet = workbook.createSheet(INVALID_ROWS_SHEET);

        final Row header = sheet.createRow(0);
        for (final InvalidRowColumn column : InvalidRowColumn.values()) {
            header.createCell(column.getIndex()).setCellValue(column.getHeader());
            sheet.setColumnWidth(column.getIndex(), column.getWidthChars() * EXCEL_WIDTH_UNITS_PER_CHAR);
        }

        for (int i = 0; i < rows.size(); i++) {
            final Row row = sheet.createRow(i + 1);
            final InvalidAdjustmentRow data = rows.get(i);
            row.createCell(InvalidRowColumn.ROW_NUMBER.getIndex()).setCellValue(data.rowNumber());
            row.createCell(InvalidRowColumn.STORE.getIndex()).setCellValue(data.store());
            row.createCell(InvalidRowColumn.DATE.getIndex()).setCellValue(data.date());
            row.createCell(InvalidRowColumn.TIME_RANGES.getIndex()).setCellValue(data.timeRanges());
            row.createCell(InvalidRowColumn.AMOUNT.getIndex()).setCellValue(data.amount());
            row.createCell(InvalidRowColumn.REASON.getIndex()).setCellValue(data.reason());
        }
    }
}
