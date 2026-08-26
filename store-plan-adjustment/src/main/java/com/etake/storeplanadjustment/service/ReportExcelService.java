package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.config.properties.SystemConfigurationProperties;
import com.etake.storeplanadjustment.model.AdjustedPlanReportRow;
import com.etake.storeplanadjustment.model.ReportColumn;
import com.etake.storeplanadjustment.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import static com.etake.storeplanadjustment.utils.Constants.RESULT_SHEET;
import static com.etake.storeplanadjustment.utils.Constants.TURNOVER_FORMAT;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExcelService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SystemConfigurationProperties properties;

    public void writeWorkbook(final List<AdjustedPlanReportRow> rows) throws Exception {
        final File outputFile = ResourcePaths.resolve(properties.resources().output());
        final File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (Workbook workbook = new XSSFWorkbook();
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            fill(workbook, rows);
            workbook.write(outputStream);
        }
        log.info("Wrote {} adjusted plan row(s) to {}", rows.size(), outputFile.getAbsolutePath());
    }

    private static void fill(final Workbook workbook, final List<AdjustedPlanReportRow> rows) {
        final Sheet sheet = workbook.createSheet(RESULT_SHEET);

        final Row header = sheet.createRow(0);
        for (final ReportColumn column : ReportColumn.values()) {
            header.createCell(column.getIndex()).setCellValue(column.getHeader());
        }

        final CellStyle numberStyle = workbook.createCellStyle();
        numberStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(TURNOVER_FORMAT));

        for (int i = 0; i < rows.size(); i++) {
            final Row row = sheet.createRow(i + 1);
            final AdjustedPlanReportRow data = rows.get(i);
            row.createCell(ReportColumn.DATE.getIndex()).setCellValue(data.date().format(DATE_FORMAT));
            row.createCell(ReportColumn.STORE.getIndex()).setCellValue(data.store());
            row.createCell(ReportColumn.CATEGORY.getIndex()).setCellValue(data.category());
            setNumber(row.createCell(ReportColumn.TURNOVER.getIndex()), data.turnover(), numberStyle);
            setNumber(row.createCell(ReportColumn.MARGIN.getIndex()), data.margin(), numberStyle);
            setNumber(row.createCell(ReportColumn.ADJUSTED_TURNOVER.getIndex()), data.adjustedTurnover(), numberStyle);
        }
    }

    private static void setNumber(final Cell cell, final BigDecimal value, final CellStyle style) {
        if (Objects.nonNull(value)) {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(style);
        }
    }
}
