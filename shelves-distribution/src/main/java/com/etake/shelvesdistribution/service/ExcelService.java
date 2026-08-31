package com.etake.shelvesdistribution.service;

import com.etake.shelvesdistribution.model.CategoryPerformance;
import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import com.etake.shelvesdistribution.utils.ResourcePaths;
import com.excel.custom.library.service.ExcelFormatService;
import com.excel.custom.library.util.ExcelUtils;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private static final String SHEET_NAME = "result";
    private static final String SEASONAL_CATEGORY = "0-Сезонні товари";

    private final ExcelFormatService excelFormatService;

    @Value("${system-configurations.output}")
    private String output;

    private static final int SALES_WEIGHT = 2;
    private static final int MARGIN_WEIGHT = 3;

    private static final String COL_STORE = "A";
    private static final String COL_CATEGORY = "B";
    private static final String COL_SALES = "C";
    private static final String COL_COST_SALES = "D";
    private static final String COL_MARGIN = "E";
    private static final String COL_BALANCE = "F";
    private static final String COL_SALES_SHARE = "G";
    private static final String COL_MARGIN_SHARE = "H";
    private static final String COL_TURNOVER = "I";
    private static final String COL_WEIGHTED_SALES = "J";
    private static final String COL_WEIGHTED_TURNOVER = "L";
    private static final String COL_TOTAL = "M";
    private static final String COL_RATING = "N";
    private static final String COL_LOOKUP_STORE = "Q";
    private static final String COL_LOOKUP_MAX_SHELVES = "R";

    private static final int IDX_STORE = 0;
    private static final int IDX_CATEGORY = 1;
    private static final int IDX_SALES = 2;
    private static final int IDX_COST_SALES = 3;
    private static final int IDX_MARGIN = 4;
    private static final int IDX_BALANCE = 5;
    private static final int IDX_SALES_SHARE = 6;
    private static final int IDX_MARGIN_SHARE = 7;
    private static final int IDX_TURNOVER = 8;
    private static final int IDX_WEIGHTED_SALES = 9;
    private static final int IDX_WEIGHTED_MARGIN = 10;
    private static final int IDX_WEIGHTED_TURNOVER = 11;
    private static final int IDX_TOTAL = 12;
    private static final int IDX_RATING = 13;
    private static final int IDX_SHELF_COUNT = 14;
    private static final int IDX_LOOKUP_STORE = 16;
    private static final int IDX_LOOKUP_MAX_SHELVES = 17;
    private static final int FLAT_LAST_COLUMN_INDEX = IDX_LOOKUP_MAX_SHELVES;

    private static final String[] FLAT_HEADERS = {
            "Магазин", "Категорія", "Продажі РЦ", "Продажі ПЦ", "Маржа", "Залишки ПЦ",
            "Доля продаж", "Доля маржі", "Оборот", "Р Продажі", "Р Маржа", "Р Оборот",
            "Разом", "Рейтинг", "К-сть стелажів"
    };

    public void generateReport(List<StoreCategoryPerformance> storeCategories) throws IOException {
        String fileName = String.format("Розподіл стелажів %s.xlsx", LocalDate.now());
        List<StoreCategoryPerformance> sorted = storeCategories.stream().sorted().toList();
        List<String> stores = sorted.stream().map(StoreCategoryPerformance::store).distinct().toList();
        writeWorkbook(fileName, SHEET_NAME, (workbook, sheet) -> {
            writeFlatHeaders(workbook, sheet);
            writeFlatDataRows(sheet, sorted);
            writeFlatLookupTable(sheet, stores);
            applyFlatFormatting(workbook, sheet, sorted.size(), stores.size());

            sheet.createFreezePane(0, 1);
            for (int i = 0; i <= FLAT_LAST_COLUMN_INDEX; i++) {
                sheet.autoSizeColumn(i);
            }
        });
    }

    private void writeFlatHeaders(Workbook workbook, Sheet sheet) {
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = excelFormatService.getHeaderStyle(workbook);
        for (int i = 0; i < FLAT_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(FLAT_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
        setHeaderCell(headerRow, IDX_LOOKUP_STORE, "Магазин", headerStyle);
        setHeaderCell(headerRow, IDX_LOOKUP_MAX_SHELVES, "Макс. к-сть стелажів", headerStyle);
    }

    private void writeFlatDataRows(Sheet sheet, List<StoreCategoryPerformance> storeCategories) {
        for (int i = 0; i < storeCategories.size(); i++) {
            StoreCategoryPerformance data = storeCategories.get(i);
            int poiRow = i + 1;
            int r = poiRow + 1;
            Row row = sheet.createRow(poiRow);

            row.createCell(IDX_STORE).setCellValue(data.store());
            row.createCell(IDX_CATEGORY).setCellValue(data.category());
            row.createCell(IDX_SALES).setCellValue(data.discountSales().doubleValue());
            row.createCell(IDX_COST_SALES).setCellValue(data.costSales().doubleValue());
            row.createCell(IDX_MARGIN).setCellFormula(String.format("%s%d-%s%d", COL_SALES, r, COL_COST_SALES, r));
            row.createCell(IDX_BALANCE).setCellValue(data.currentCostBalance().doubleValue());
            row.createCell(IDX_SALES_SHARE).setCellFormula(String.format(
                    "%s%d/SUMIFS(%s:%s,%s:%s,%s%d)", COL_SALES, r, COL_SALES, COL_SALES, COL_STORE, COL_STORE, COL_STORE, r));
            row.createCell(IDX_MARGIN_SHARE).setCellFormula(String.format(
                    "%s%d/SUMIFS(%s:%s,%s:%s,%s%d)", COL_MARGIN, r, COL_MARGIN, COL_MARGIN, COL_STORE, COL_STORE, COL_STORE, r));
            row.createCell(IDX_TURNOVER).setCellFormula(String.format(
                    "IF(%s%d=\"%s\",AVERAGEIFS(%s:%s,%s:%s,%s%d,%s:%s,\"<>%s\"),IFERROR(%s%d/%s%d/12,0))",
                    COL_CATEGORY, r, SEASONAL_CATEGORY,
                    COL_TURNOVER, COL_TURNOVER, COL_STORE, COL_STORE, COL_STORE, r, COL_CATEGORY, COL_CATEGORY, SEASONAL_CATEGORY,
                    COL_COST_SALES, r, COL_BALANCE, r));
            row.createCell(IDX_WEIGHTED_SALES).setCellFormula(String.format("%s%d*%d", COL_SALES_SHARE, r, SALES_WEIGHT));
            row.createCell(IDX_WEIGHTED_MARGIN).setCellFormula(String.format("%s%d*%d", COL_MARGIN_SHARE, r, MARGIN_WEIGHT));
            row.createCell(IDX_WEIGHTED_TURNOVER).setCellFormula(String.format(
                    "%s%d/SUMIF(%s:%s,%s%d,%s:%s)", COL_TURNOVER, r, COL_STORE, COL_STORE, COL_STORE, r, COL_TURNOVER, COL_TURNOVER));
            row.createCell(IDX_TOTAL).setCellFormula(String.format(
                    "SUM(%s%d:%s%d)", COL_WEIGHTED_SALES, r, COL_WEIGHTED_TURNOVER, r));
            row.createCell(IDX_RATING).setCellFormula(String.format(
                    "%s%d/SUMIF(%s:%s,%s%d,%s:%s)", COL_TOTAL, r, COL_STORE, COL_STORE, COL_STORE, r, COL_TOTAL, COL_TOTAL));
            row.createCell(IDX_SHELF_COUNT).setCellFormula(String.format(
                    "_xlfn.XLOOKUP(%s%d,%s:%s,%s:%s)*%s%d",
                    COL_STORE, r, COL_LOOKUP_STORE, COL_LOOKUP_STORE, COL_LOOKUP_MAX_SHELVES, COL_LOOKUP_MAX_SHELVES, COL_RATING, r));
        }
    }

    private void writeFlatLookupTable(Sheet sheet, List<String> stores) {
        for (int i = 0; i < stores.size(); i++) {
            Row row = sheet.getRow(i + 1);
            if (row == null) {
                row = sheet.createRow(i + 1);
            }
            row.createCell(IDX_LOOKUP_STORE).setCellValue(stores.get(i));
            row.createCell(IDX_LOOKUP_MAX_SHELVES);
        }
    }

    private void applyFlatFormatting(Workbook workbook, Sheet sheet, int lastDataRow, int lookupRowCount) {
        ExcelUtils.applyBorders(workbook, sheet, 1, IDX_STORE, lastDataRow, IDX_SHELF_COUNT, BorderStyle.THIN);
        ExcelUtils.applyBorders(workbook, sheet, 1, IDX_LOOKUP_STORE, lookupRowCount, IDX_LOOKUP_MAX_SHELVES, BorderStyle.THIN);

        ExcelUtils.applyDataFormat(workbook, sheet, 1, IDX_SALES, lastDataRow, IDX_BALANCE, "#,##0.00");
        ExcelUtils.applyDataFormat(workbook, sheet, 1, IDX_SALES_SHARE, lastDataRow, IDX_RATING, "0.00%");
        ExcelUtils.applyDataFormat(workbook, sheet, 1, IDX_SHELF_COUNT, lastDataRow, IDX_SHELF_COUNT, "0.00");
        ExcelUtils.applyDataFormat(workbook, sheet, 1, IDX_LOOKUP_MAX_SHELVES, lookupRowCount, IDX_LOOKUP_MAX_SHELVES, "0");
    }

    private static final String AGG_COL_CATEGORY = "A";
    private static final String AGG_COL_SALES = "B";
    private static final String AGG_COL_MARGIN = "C";
    private static final String AGG_COL_COST_SALES = "D";
    private static final String AGG_COL_BALANCE = "E";
    private static final String AGG_COL_SALES_SHARE = "F";
    private static final String AGG_COL_MARGIN_SHARE = "G";
    private static final String AGG_COL_TURNOVER = "H";
    private static final String AGG_COL_WEIGHTED_SALES = "I";
    private static final String AGG_COL_WEIGHTED_MARGIN = "J";
    private static final String AGG_COL_WEIGHTED_TURNOVER = "K";
    private static final String AGG_COL_TOTAL = "L";
    private static final String AGG_COL_RATING = "M";
    private static final String AGG_COL_SHELF_COUNT = "N";
    private static final String AGG_COL_ACCEPTED = "O";

    private static final int AGG_IDX_CATEGORY = 0;
    private static final int AGG_IDX_SALES = 1;
    private static final int AGG_IDX_MARGIN = 2;
    private static final int AGG_IDX_COST_SALES = 3;
    private static final int AGG_IDX_BALANCE = 4;
    private static final int AGG_IDX_SALES_SHARE = 5;
    private static final int AGG_IDX_MARGIN_SHARE = 6;
    private static final int AGG_IDX_TURNOVER = 7;
    private static final int AGG_IDX_WEIGHTED_SALES = 8;
    private static final int AGG_IDX_WEIGHTED_MARGIN = 9;
    private static final int AGG_IDX_WEIGHTED_TURNOVER = 10;
    private static final int AGG_IDX_TOTAL = 11;
    private static final int AGG_IDX_RATING = 12;
    private static final int AGG_IDX_SHELF_COUNT = 13;
    private static final int AGG_IDX_ACCEPTED = 14;
    private static final int AGG_IDX_DIFFERENCE = 15;
    private static final int AGG_LAST_COLUMN_INDEX = AGG_IDX_DIFFERENCE;
    private static final int AGG_FIRST_DATA_ROW = 3;

    private static final int AGG_IDX_SALES_WEIGHT = AGG_IDX_WEIGHTED_SALES;
    private static final int AGG_IDX_MARGIN_WEIGHT = AGG_IDX_WEIGHTED_MARGIN;
    private static final int AGG_IDX_TURNOVER_WEIGHT = AGG_IDX_WEIGHTED_TURNOVER;
    private static final int AGG_IDX_MAX_SHELVES_LABEL = AGG_IDX_TOTAL;
    private static final int AGG_IDX_RATING_SCALE = AGG_IDX_SHELF_COUNT;
    private static final int AGG_RATING_SCALE = 100;

    private static final String[] AGG_HEADERS = {
            "Категорія", "Продажі_РЦ", "Маржа", "Продажі_ПЦ", "Залишки_ПЦ",
            "Доля продаж", "Доля маржі", "Оборот", "Р Продажі", "Р Маржа", "Р Оборот",
            "Разом", "Рейтинг", "К-сть стелажів", "Приймаємо", "Різниця"
    };

    public void generateReport(List<CategoryPerformance> categories, String label) throws IOException {
        String fileName = String.format("Розподіл стелажів %s.xlsx", LocalDate.now());
        List<CategoryPerformance> sorted = categories.stream().sorted().toList();
        int first = AGG_FIRST_DATA_ROW;
        int last = first + sorted.size() - 1;
        writeWorkbook(fileName, sanitizeSheetName(label), (workbook, sheet) -> {
            writeAggregateConfigRow(workbook, sheet);
            writeAggregateHeaders(workbook, sheet);
            writeAggregateDataRows(sheet, sorted, first, last);
            writeAggregateTotalsRow(workbook, sheet, first, last);
            applyAggregateFormatting(workbook, sheet, first - 1, last - 1);

            sheet.createFreezePane(0, 2);
            for (int i = 0; i <= AGG_LAST_COLUMN_INDEX; i++) {
                sheet.autoSizeColumn(i);
            }
        });
    }

    private void writeAggregateConfigRow(Workbook workbook, Sheet sheet) {
        Row configRow = sheet.createRow(0);
        for (int i = 0; i <= AGG_LAST_COLUMN_INDEX; i++) {
            configRow.createCell(i);
        }
        configRow.getCell(AGG_IDX_SALES_WEIGHT).setCellValue(SALES_WEIGHT);
        configRow.getCell(AGG_IDX_MARGIN_WEIGHT).setCellValue(MARGIN_WEIGHT);
        configRow.getCell(AGG_IDX_TURNOVER_WEIGHT).setCellValue(1);
        configRow.getCell(AGG_IDX_MAX_SHELVES_LABEL).setCellValue("Макс. стелажів");
        configRow.getCell(AGG_IDX_RATING_SCALE).setCellValue(AGG_RATING_SCALE);

        ExcelUtils.applyBorders(workbook, sheet, 0, AGG_IDX_CATEGORY, 0, AGG_LAST_COLUMN_INDEX, BorderStyle.THIN);

        CellStyle accentStyle = excelFormatService.getHeaderStyle(workbook, IndexedColors.DARK_YELLOW);
        for (int i = AGG_IDX_SALES_WEIGHT; i <= AGG_IDX_MAX_SHELVES_LABEL; i++) {
            configRow.getCell(i).setCellStyle(accentStyle);
        }
        CellStyle weightStyle = workbook.createCellStyle();
        weightStyle.cloneStyleFrom(accentStyle);
        weightStyle.setDataFormat(workbook.createDataFormat().getFormat("0%"));
        configRow.getCell(AGG_IDX_SALES_WEIGHT).setCellStyle(weightStyle);
        configRow.getCell(AGG_IDX_MARGIN_WEIGHT).setCellStyle(weightStyle);
        configRow.getCell(AGG_IDX_TURNOVER_WEIGHT).setCellStyle(weightStyle);

        ExcelUtils.applyBackgroundColor(workbook, sheet, 0, AGG_IDX_RATING_SCALE, 0, AGG_IDX_RATING_SCALE, IndexedColors.YELLOW);
    }

    private void writeAggregateHeaders(Workbook workbook, Sheet sheet) {
        Row headerRow = sheet.createRow(1);
        CellStyle headerStyle = excelFormatService.getHeaderStyle(workbook);
        for (int i = 0; i < AGG_HEADERS.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(AGG_HEADERS[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void writeAggregateDataRows(Sheet sheet, List<CategoryPerformance> categories, int first, int last) {
        for (int i = 0; i < categories.size(); i++) {
            CategoryPerformance data = categories.get(i);
            int r = first + i;
            Row row = sheet.createRow(r - 1);
            BigDecimal margin = data.discountSales().subtract(data.costSales());

            row.createCell(AGG_IDX_CATEGORY).setCellValue(data.category());
            row.createCell(AGG_IDX_SALES).setCellValue(data.discountSales().doubleValue());
            row.createCell(AGG_IDX_MARGIN).setCellValue(margin.doubleValue());
            row.createCell(AGG_IDX_COST_SALES).setCellFormula(String.format("%s%d-%s%d", AGG_COL_SALES, r, AGG_COL_MARGIN, r));
            row.createCell(AGG_IDX_BALANCE).setCellValue(data.currentCostBalance().doubleValue());
            row.createCell(AGG_IDX_SALES_SHARE).setCellFormula(String.format(
                    "%s%d/SUM(%s%d:%s%d)", AGG_COL_SALES, r, AGG_COL_SALES, first, AGG_COL_SALES, last));
            row.createCell(AGG_IDX_MARGIN_SHARE).setCellFormula(String.format(
                    "%s%d/SUM(%s%d:%s%d)", AGG_COL_MARGIN, r, AGG_COL_MARGIN, first, AGG_COL_MARGIN, last));
            row.createCell(AGG_IDX_TURNOVER).setCellFormula(String.format(
                    "IF(%s%d=\"%s\",AVERAGEIF(%s%d:%s%d,\"<>%s\",%s%d:%s%d),IFERROR(%s%d/%s%d,0))",
                    AGG_COL_CATEGORY, r, SEASONAL_CATEGORY,
                    AGG_COL_CATEGORY, first, AGG_COL_CATEGORY, last, SEASONAL_CATEGORY, AGG_COL_TURNOVER, first, AGG_COL_TURNOVER, last,
                    AGG_COL_COST_SALES, r, AGG_COL_BALANCE, r));
            row.createCell(AGG_IDX_WEIGHTED_SALES).setCellFormula(String.format(
                    "%s$1*%s%d", AGG_COL_WEIGHTED_SALES, AGG_COL_SALES_SHARE, r));
            row.createCell(AGG_IDX_WEIGHTED_MARGIN).setCellFormula(String.format(
                    "%s$1*%s%d", AGG_COL_WEIGHTED_MARGIN, AGG_COL_MARGIN_SHARE, r));
            row.createCell(AGG_IDX_WEIGHTED_TURNOVER).setCellFormula(String.format(
                    "%s$1*%s%d/SUM(%s%d:%s%d)", AGG_COL_WEIGHTED_TURNOVER, AGG_COL_TURNOVER, r, AGG_COL_TURNOVER, first, AGG_COL_TURNOVER, last));
            row.createCell(AGG_IDX_TOTAL).setCellFormula(String.format(
                    "SUM(%s%d:%s%d)", AGG_COL_WEIGHTED_SALES, r, AGG_COL_WEIGHTED_TURNOVER, r));
            row.createCell(AGG_IDX_RATING).setCellFormula(String.format(
                    "%s%d/SUM(%s%d:%s%d)", AGG_COL_TOTAL, r, AGG_COL_TOTAL, first, AGG_COL_TOTAL, last));
            row.createCell(AGG_IDX_SHELF_COUNT).setCellFormula(String.format(
                    "IF(%s$1*%s%d=0,\"\",%s$1*%s%d)", AGG_COL_SHELF_COUNT, AGG_COL_RATING, r, AGG_COL_SHELF_COUNT, AGG_COL_RATING, r));
            row.createCell(AGG_IDX_ACCEPTED);
            row.createCell(AGG_IDX_DIFFERENCE).setCellFormula(String.format(
                    "IF(%s%d=\"\",\"\",%s%d-%s%d)", AGG_COL_ACCEPTED, r, AGG_COL_ACCEPTED, r, AGG_COL_SHELF_COUNT, r));
        }
    }

    private void writeAggregateTotalsRow(Workbook workbook, Sheet sheet, int first, int last) {
        int totalsExcelRow = last + 1;
        int totalsPoiRow = totalsExcelRow - 1;
        Row row = sheet.createRow(totalsPoiRow);
        for (int i = 0; i <= AGG_LAST_COLUMN_INDEX; i++) {
            row.createCell(i);
        }
        row.getCell(AGG_IDX_CATEGORY).setCellValue("Разом");
        row.getCell(AGG_IDX_SALES).setCellFormula(subtotal(AGG_COL_SALES, first, last));
        row.getCell(AGG_IDX_MARGIN).setCellFormula(subtotal(AGG_COL_MARGIN, first, last));
        row.getCell(AGG_IDX_COST_SALES).setCellFormula(subtotal(AGG_COL_COST_SALES, first, last));
        row.getCell(AGG_IDX_BALANCE).setCellFormula(subtotal(AGG_COL_BALANCE, first, last));
        row.getCell(AGG_IDX_SALES_SHARE).setCellFormula(subtotal(AGG_COL_SALES_SHARE, first, last));
        row.getCell(AGG_IDX_MARGIN_SHARE).setCellFormula(subtotal(AGG_COL_MARGIN_SHARE, first, last));
        row.getCell(AGG_IDX_TURNOVER).setCellFormula(subtotal(AGG_COL_TURNOVER, first, last));
        row.getCell(AGG_IDX_TOTAL).setCellFormula(subtotal(AGG_COL_TOTAL, first, last));
        row.getCell(AGG_IDX_RATING).setCellFormula(subtotal(AGG_COL_RATING, first, last));
        row.getCell(AGG_IDX_SHELF_COUNT).setCellFormula(String.format(
                "IF(%s$1*%s%d=0,\"\",%s$1*%s%d)",
                AGG_COL_SHELF_COUNT, AGG_COL_RATING, totalsExcelRow, AGG_COL_SHELF_COUNT, AGG_COL_RATING, totalsExcelRow));
        row.getCell(AGG_IDX_DIFFERENCE).setCellFormula(String.format(
                "IF(%s%d=\"\",\"\",%s%d-%s%d)",
                AGG_COL_ACCEPTED, totalsExcelRow, AGG_COL_ACCEPTED, totalsExcelRow, AGG_COL_SHELF_COUNT, totalsExcelRow));

        CellStyle totalsStyle = excelFormatService.getHeaderStyle(workbook, IndexedColors.BLACK);
        for (int i = 0; i <= AGG_LAST_COLUMN_INDEX; i++) {
            row.getCell(i).setCellStyle(totalsStyle);
        }
        ExcelUtils.applyDataFormat(workbook, sheet, totalsPoiRow, AGG_IDX_SALES, totalsPoiRow, AGG_IDX_BALANCE, "#,##0.00");
        ExcelUtils.applyDataFormat(workbook, sheet, totalsPoiRow, AGG_IDX_SALES_SHARE, totalsPoiRow, AGG_IDX_RATING, "0.00%");
        ExcelUtils.applyDataFormat(workbook, sheet, totalsPoiRow, AGG_IDX_SHELF_COUNT, totalsPoiRow, AGG_IDX_DIFFERENCE, "0.00");
    }

    private static String subtotal(String column, int first, int last) {
        return String.format("SUBTOTAL(9,%s%d:%s%d)", column, first, column, last);
    }

    private void applyAggregateFormatting(Workbook workbook, Sheet sheet, int firstDataRow, int lastDataRow) {
        ExcelUtils.applyBorders(workbook, sheet, firstDataRow, AGG_IDX_CATEGORY, lastDataRow, AGG_IDX_DIFFERENCE, BorderStyle.THIN);

        ExcelUtils.applyDataFormat(workbook, sheet, firstDataRow, AGG_IDX_SALES, lastDataRow, AGG_IDX_BALANCE, "#,##0.00");
        ExcelUtils.applyDataFormat(workbook, sheet, firstDataRow, AGG_IDX_SALES_SHARE, lastDataRow, AGG_IDX_RATING, "0.00%");
        ExcelUtils.applyDataFormat(workbook, sheet, firstDataRow, AGG_IDX_SHELF_COUNT, lastDataRow, AGG_IDX_DIFFERENCE, "0.00");
    }

    private void writeWorkbook(String fileName, String sheetName, BiConsumer<Workbook, Sheet> sheetBuilder) throws IOException {
        File directory = ResourcePaths.resolve(output);
        directory.mkdirs();
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fileOutputStream = new FileOutputStream(new File(directory, fileName))) {
            Sheet sheet = workbook.createSheet(sheetName);
            sheetBuilder.accept(workbook, sheet);
            workbook.setForceFormulaRecalculation(true);
            workbook.write(fileOutputStream);
        }
    }

    private static String sanitizeSheetName(String label) {
        if (label == null || label.isBlank()) {
            return SHEET_NAME;
        }
        String sanitized = label.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (sanitized.isEmpty()) {
            return SHEET_NAME;
        }
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private static void setHeaderCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
