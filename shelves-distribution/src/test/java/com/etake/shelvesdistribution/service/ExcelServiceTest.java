package com.etake.shelvesdistribution.service;

import com.etake.shelvesdistribution.model.CategoryPerformance;
import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import com.excel.custom.library.service.impl.ExcelFormatServiceImpl;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelServiceTest {

    @TempDir
    private File outputDir;

    private final ExcelService excelService = new ExcelService(new ExcelFormatServiceImpl());

    @Test
    void generateReport_groupedByStore_writesFlatLayoutSortedByStoreThenCategory() throws IOException, InvalidFormatException {
        ReflectionTestUtils.setField(excelService, "output", outputDir.getAbsolutePath());

        excelService.generateReport(List.of(
                new StoreCategoryPerformance("Store B", "Toys", new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("5")),
                new StoreCategoryPerformance("Store A", "Zebra", new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("10")),
                new StoreCategoryPerformance("Store A", "Apple", new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("2"))
        ));

        File[] generated = outputDir.listFiles((_, name) -> name.startsWith("Розподіл стелажів") && name.endsWith(".xlsx"));
        assertThat(generated).isNotNull().hasSize(1);

        try (XSSFWorkbook workbook = new XSSFWorkbook(Objects.requireNonNull(generated)[0])) {
            Sheet sheet = workbook.getSheet("result");
            assertThat(sheet).isNotNull();

            Row header = sheet.getRow(0);
            assertThat(cellString(header, 0)).isEqualTo("Магазин");
            assertThat(cellString(header, 1)).isEqualTo("Категорія");
            assertThat(cellString(header, 14)).isEqualTo("К-сть стелажів");
            assertThat(cellString(header, 16)).isEqualTo("Магазин");
            assertThat(cellString(header, 17)).isEqualTo("Макс. к-сть стелажів");

            Row firstDataRow = sheet.getRow(1);
            assertThat(cellString(firstDataRow, 0)).isEqualTo("Store A");
            assertThat(cellString(firstDataRow, 1)).isEqualTo("Apple");
            assertThat(firstDataRow.getCell(2).getNumericCellValue()).isEqualTo(30.0);
            assertThat(firstDataRow.getCell(4).getCellType()).isEqualTo(CellType.FORMULA);
            assertThat(firstDataRow.getCell(4).getCellFormula()).isEqualTo("C2-D2");
            assertThat(firstDataRow.getCell(14).getCellFormula()).isEqualTo("_xlfn.XLOOKUP(A2,Q:Q,R:R)*N2");
            assertThat(firstDataRow.getCell(2).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            assertThat(firstDataRow.getCell(6).getCellStyle().getDataFormatString()).isEqualTo("0.00%");
            assertThat(firstDataRow.getCell(0).getCellStyle().getBorderBottom()).isEqualTo(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            Row secondDataRow = sheet.getRow(2);
            assertThat(cellString(secondDataRow, 0)).isEqualTo("Store A");
            assertThat(cellString(secondDataRow, 1)).isEqualTo("Zebra");

            Row thirdDataRow = sheet.getRow(3);
            assertThat(cellString(thirdDataRow, 0)).isEqualTo("Store B");
            assertThat(cellString(thirdDataRow, 1)).isEqualTo("Toys");

            assertThat(cellString(sheet.getRow(1), 16)).isEqualTo("Store A");
            assertThat(cellString(sheet.getRow(2), 16)).isEqualTo("Store B");
            assertThat(sheet.getRow(2).getCell(17).getCellType()).isEqualTo(CellType.BLANK);
        }
    }

    @Test
    void generateReport_notGroupedByStore_writesAggregateLayoutSortedByCategory() throws IOException, InvalidFormatException {
        ReflectionTestUtils.setField(excelService, "output", outputDir.getAbsolutePath());

        excelService.generateReport(List.of(
                new CategoryPerformance("Zebra", new BigDecimal("150"), new BigDecimal("80"), new BigDecimal("15")),
                new CategoryPerformance("Apple", new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("2")),
                new CategoryPerformance("Mango", new BigDecimal("20"), new BigDecimal("5"), new BigDecimal("1"))
        ), "Ковельські магазини");

        File[] generated = outputDir.listFiles((_, name) -> name.startsWith("Розподіл стелажів") && name.endsWith(".xlsx"));
        assertThat(generated).isNotNull().hasSize(1);

        try (XSSFWorkbook workbook = new XSSFWorkbook(Objects.requireNonNull(generated)[0])) {
            Sheet sheet = workbook.getSheet("Ковельські магазини");
            assertThat(sheet).isNotNull();

            Row configRow = sheet.getRow(0);
            assertThat(configRow.getCell(8).getNumericCellValue()).isEqualTo(2.0);
            assertThat(configRow.getCell(8).getCellStyle().getDataFormatString()).isEqualTo("0%");
            assertThat(configRow.getCell(9).getNumericCellValue()).isEqualTo(3.0);
            assertThat(configRow.getCell(10).getNumericCellValue()).isEqualTo(1.0);
            assertThat(cellString(configRow, 11)).isEqualTo("Макс. стелажів");
            assertThat(configRow.getCell(12).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(configRow.getCell(13).getNumericCellValue()).isEqualTo(100.0);
            for (int i = 0; i <= 15; i++) {
                assertThat(configRow.getCell(i)).as("config row cell %d should exist", i).isNotNull();
            }
            for (int i = 8; i <= 11; i++) {
                assertThat(configRow.getCell(i).getCellStyle().getFillForegroundColor())
                        .as("config row cell %d should have the olive accent", i)
                        .isEqualTo(org.apache.poi.ss.usermodel.IndexedColors.DARK_YELLOW.getIndex());
            }
            for (int i : new int[]{0, 1, 6, 7, 12, 14, 15}) {
                assertThat(configRow.getCell(i).getCellStyle().getFillPattern())
                        .as("config row cell %d should have no fill", i)
                        .isEqualTo(org.apache.poi.ss.usermodel.FillPatternType.NO_FILL);
            }
            assertThat(configRow.getCell(13).getCellStyle().getFillForegroundColor())
                    .isEqualTo(org.apache.poi.ss.usermodel.IndexedColors.YELLOW.getIndex());

            Row header = sheet.getRow(1);
            assertThat(cellString(header, 0)).isEqualTo("Категорія");
            assertThat(cellString(header, 13)).isEqualTo("К-сть стелажів");
            assertThat(cellString(header, 14)).isEqualTo("Приймаємо");
            assertThat(cellString(header, 15)).isEqualTo("Різниця");

            Row firstDataRow = sheet.getRow(2);
            assertThat(cellString(firstDataRow, 0)).isEqualTo("Apple");
            assertThat(firstDataRow.getCell(1).getNumericCellValue()).isEqualTo(30.0);
            assertThat(firstDataRow.getCell(2).getNumericCellValue()).isEqualTo(20.0);
            assertThat(firstDataRow.getCell(3).getCellFormula()).isEqualTo("B3-C3");
            assertThat(firstDataRow.getCell(8).getCellFormula()).isEqualTo("I$1*F3");
            assertThat(firstDataRow.getCell(13).getCellFormula()).isEqualTo("IF(N$1*M3=0,\"\",N$1*M3)");
            assertThat(firstDataRow.getCell(14).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(firstDataRow.getCell(15).getCellFormula()).isEqualTo("IF(O3=\"\",\"\",O3-N3)");
            assertThat(firstDataRow.getCell(1).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
            assertThat(firstDataRow.getCell(5).getCellStyle().getDataFormatString()).isEqualTo("0.00%");
            assertThat(firstDataRow.getCell(0).getCellStyle().getBorderTop()).isEqualTo(org.apache.poi.ss.usermodel.BorderStyle.THIN);

            assertThat(cellString(sheet.getRow(3), 0)).isEqualTo("Mango");
            assertThat(cellString(sheet.getRow(4), 0)).isEqualTo("Zebra");
            assertThat(sheet.getRow(3).getCell(3).getCellFormula()).isEqualTo("B4-C4");

            Row totalsRow = sheet.getRow(5);
            assertThat(cellString(totalsRow, 0)).isEqualTo("Разом");
            assertThat(totalsRow.getCell(1).getCellFormula()).isEqualTo("SUBTOTAL(9,B3:B5)");
            assertThat(totalsRow.getCell(4).getCellFormula()).isEqualTo("SUBTOTAL(9,E3:E5)");
            assertThat(totalsRow.getCell(5).getCellFormula()).isEqualTo("SUBTOTAL(9,F3:F5)");
            assertThat(totalsRow.getCell(11).getCellFormula()).isEqualTo("SUBTOTAL(9,L3:L5)");
            assertThat(totalsRow.getCell(12).getCellFormula()).isEqualTo("SUBTOTAL(9,M3:M5)");
            assertThat(totalsRow.getCell(13).getCellFormula()).isEqualTo("IF(N$1*M6=0,\"\",N$1*M6)");
            assertThat(totalsRow.getCell(15).getCellFormula()).isEqualTo("IF(O6=\"\",\"\",O6-N6)");
            assertThat(totalsRow.getCell(8).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(totalsRow.getCell(9).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(totalsRow.getCell(10).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(totalsRow.getCell(0).getCellStyle().getFillForegroundColor())
                    .isNotEqualTo(firstDataRow.getCell(0).getCellStyle().getFillForegroundColor());
        }
    }

    private static String cellString(Row row, int column) {
        Cell cell = row.getCell(column);
        return cell == null ? null : cell.getStringCellValue();
    }
}
