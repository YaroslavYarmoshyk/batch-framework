package com.etake.turnoverplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.math.BigDecimal;

import static com.etake.turnoverplan.utils.Constants.COLUMN_WIDTH;
import static com.etake.turnoverplan.utils.Constants.DEFAULT_COLUMN_WIDTH;
import static com.etake.turnoverplan.utils.Constants.FIRST_ROW_INDEX;
import static com.etake.turnoverplan.utils.Constants.SECOND_ROW_INDEX;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class ExcelUtils {

    public static <T> void setValue(final Row row, final int col, final T value) {
        final Cell cell = row.getCell(col);
        if (nonNull(value)) {
            if (value instanceof Number number) {
                final BigDecimal bigDecimal = BigDecimal.valueOf(number.doubleValue());
                if (bigDecimal.compareTo(BigDecimal.ZERO) > 0) {
                    cell.setCellValue(bigDecimal.doubleValue());
                }
            } else {
                cell.setCellValue(value.toString());
            }
        }
    }

    public static void createCells(final Sheet sheet, final int endRow, final int endCol) {
        for (int i = 0; i <= endRow; i++) {
            final Row row = sheet.createRow(i);
            for (int j = 0; j <= endCol; j++) {
                row.createCell(j);
            }
        }
    }

    public static void applyCellStyle(final Sheet sheet,
                                      final CellStyle cellStyle,
                                      final int startRow,
                                      final int startColumn,
                                      final int endRow,
                                      final int endColumn) {
        for (int i = startRow; i <= endRow; i++) {
            final Row row = sheet.getRow(i);
            for (int j = startColumn; j <= endColumn; j++) {
                final Cell cell = row.getCell(j);
                cell.setCellStyle(cellStyle);
            }
        }
    }

    public static void applyCellStyle(final Sheet sheet,
                                      final CellStyle cellStyle,
                                      final int row,
                                      final int col) {
        applyCellStyle(sheet, cellStyle, row, col, row, col);
    }

    public static void autosizeColumns(final Sheet sheet) {
        final Row row = sheet.getRow(SECOND_ROW_INDEX);
        row.cellIterator().forEachRemaining(cell -> sheet.setColumnWidth(cell.getColumnIndex(), getWidth(sheet, cell)));
    }

    private static int getWidth(final Sheet sheet, final Cell cell) {
        final Integer widthBySecondRowValue = COLUMN_WIDTH.get(cell.getStringCellValue());
        if (isNull(widthBySecondRowValue)) {
            final Cell firstRowCell = sheet.getRow(FIRST_ROW_INDEX).getCell(cell.getColumnIndex());
            return COLUMN_WIDTH.getOrDefault(firstRowCell.getStringCellValue(), DEFAULT_COLUMN_WIDTH) * 255;
        }
        return widthBySecondRowValue * 255;
    }
}
