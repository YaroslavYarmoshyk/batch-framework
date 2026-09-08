package com.etake.cyclicforecast.writer;

import com.etake.cyclicforecast.config.properties.CyclicForecastProperties;
import com.etake.cyclicforecast.model.ForecastSummary;
import com.etake.cyclicforecast.model.LevelDistribution;
import com.etake.cyclicforecast.model.ReportRow;
import com.etake.cyclicforecast.util.ResourcePaths;
import com.excel.custom.library.service.ExcelFormatService;
import com.excel.custom.library.util.ExcelUtils;
import com.excel.custom.library.util.FormulasUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static com.etake.cyclicforecast.util.Constants.ALGORITHM_LEVEL;
import static com.etake.cyclicforecast.util.Constants.CARRYOVER;
import static com.etake.cyclicforecast.util.Constants.COLUMN_COUNT;
import static com.etake.cyclicforecast.util.Constants.DEFAULT_DATE_FORMAT;
import static com.etake.cyclicforecast.util.Constants.DONOR_KEY;
import static com.etake.cyclicforecast.util.Constants.FORECAST_QUANTITY;
import static com.etake.cyclicforecast.util.Constants.HEADERS;
import static com.etake.cyclicforecast.util.Constants.MANAGER;
import static com.etake.cyclicforecast.util.Constants.NUMBER_FORMAT;
import static com.etake.cyclicforecast.util.Constants.PRODUCT_ID;
import static com.etake.cyclicforecast.util.Constants.PRODUCT_NAME;
import static com.etake.cyclicforecast.util.Constants.PROMOTION_TYPE;
import static com.etake.cyclicforecast.util.Constants.AVG_DAILY_SALES;
import static com.etake.cyclicforecast.util.Constants.SHEET_NAME;
import static com.etake.cyclicforecast.util.Constants.STORE;
import static com.etake.cyclicforecast.util.Constants.STORE_FORMAT;
import static com.etake.cyclicforecast.util.Constants.SUBCATEGORY;
import static com.etake.cyclicforecast.util.Constants.SUMMARY_SHEET_NAME;

@Service
@RequiredArgsConstructor
public class ExcelReportService {
    private final ExcelFormatService excelFormatService;
    private final CyclicForecastProperties properties;

    public File writeWorkbook(final List<ReportRow> rows, final ForecastSummary summary) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            writeResultSheet(workbook, rows);
            writeSummarySheet(workbook, summary);

            final File output = resolveOutputFile();
            try (FileOutputStream out = new FileOutputStream(output)) {
                workbook.write(out);
            }
            return output;
        }
    }

    private void writeResultSheet(final XSSFWorkbook workbook, final List<ReportRow> rows) {
        final Sheet sheet = workbook.createSheet(SHEET_NAME);
        writeHeader(workbook, sheet);
        ExcelUtils.createCells(sheet, rows.size(), COLUMN_COUNT - 1);

        for (int i = 0; i < rows.size(); i++) {
            writeRow(workbook, sheet, i + 1, rows.get(i));
        }

        ExcelUtils.applyDataFormat(workbook, sheet, 1, AVG_DAILY_SALES, rows.size(), FORECAST_QUANTITY, NUMBER_FORMAT);
        sheet.createFreezePane(0, 1);
    }

    private void writeHeader(final XSSFWorkbook workbook, final Sheet sheet) {
        final Row header = sheet.createRow(0);
        for (int i = 0; i < HEADERS.length; i++) {
            header.createCell(i).setCellValue(HEADERS[i]);
        }
        final CellStyle headerStyle = excelFormatService.getHeaderStyle(workbook);
        ExcelUtils.applyCellStyle(sheet, headerStyle, 0, 0, 0, COLUMN_COUNT - 1);
    }

    private void writeRow(final XSSFWorkbook workbook, final Sheet sheet, final int rowIndex, final ReportRow row) {
        final Row excelRow = sheet.getRow(rowIndex);
        setValue(excelRow, MANAGER, row.manager());
        setValue(excelRow, STORE, row.store());
        setValue(excelRow, STORE_FORMAT, row.storeFormat());
        setValue(excelRow, PRODUCT_ID, row.productId());
        setValue(excelRow, PRODUCT_NAME, row.productName());
        setValue(excelRow, SUBCATEGORY, row.subcategory());
        setValue(excelRow, PROMOTION_TYPE, row.promotionType());
        setValue(excelRow, CARRYOVER, row.carryover());
        setValue(excelRow, ALGORITHM_LEVEL, row.algorithmLevel());
        setValue(excelRow, DONOR_KEY, row.donorKey());

        if (row.avgDailySales() != null) {
            setValue(excelRow, AVG_DAILY_SALES, row.avgDailySales());
            final String avgDailySalesCell = FormulasUtils.getRange(rowIndex, AVG_DAILY_SALES);
            final String carryoverCell = FormulasUtils.getRange(rowIndex, CARRYOVER);
            final String formula = FormulasUtils.getCyclicForecastFormula(avgDailySalesCell, carryoverCell, properties.demandDays());
            excelRow.getCell(FORECAST_QUANTITY).setCellFormula(formula);
        } else {
            ExcelUtils.applyBackgroundColor(workbook, sheet, rowIndex, 0, rowIndex, COLUMN_COUNT - 1, IndexedColors.ROSE);
        }
    }

    private static void setValue(final Row row, final int column, final Object value) {
        if (value != null) {
            ExcelUtils.setCellValue(row.getCell(column), value);
        }
    }

    private void writeSummarySheet(final XSSFWorkbook workbook, final ForecastSummary summary) {
        final Sheet sheet = workbook.createSheet(SUMMARY_SHEET_NAME);
        int rowIndex = 0;

        final Row title = sheet.createRow(rowIndex++);
        title.createCell(0).setCellValue("Всього рядків");
        title.createCell(1).setCellValue(summary.totalRows());

        rowIndex++;
        final Row header = sheet.createRow(rowIndex++);
        header.createCell(0).setCellValue("Алгоритм");
        header.createCell(1).setCellValue("Кількість");
        for (final LevelDistribution distribution : summary.distribution()) {
            final Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(distribution.level().display());
            row.createCell(1).setCellValue(distribution.count());
        }

        if (!summary.noneRowDiagnostics().isEmpty()) {
            rowIndex++;
            sheet.createRow(rowIndex++).createCell(0).setCellValue("Причини відсутності даних (магазин/товар: причина)");
            for (final String diagnostic : summary.noneRowDiagnostics()) {
                sheet.createRow(rowIndex++).createCell(0).setCellValue(diagnostic);
            }
        }
    }

    private File resolveOutputFile() {
        final File directory = ResourcePaths.resolve(properties.output().excelOutputDirectory());
        directory.mkdirs();
        final String fileName = "Циклічний прогноз_%s-%s.xlsx".formatted(
                properties.planningScope().fromDate().format(DEFAULT_DATE_FORMAT),
                properties.planningScope().toDate().format(DEFAULT_DATE_FORMAT));
        return new File(directory, fileName);
    }
}
