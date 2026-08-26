package com.etake.salesplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.*;

import static com.etake.salesplan.constants.ExcelConstants.SECOND_ROW_INDEX;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class ExcelUtils {

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

    public static void autosizeColumns(final Sheet sheet) {

        final Row row = sheet.getRow(SECOND_ROW_INDEX);
        row.cellIterator().forEachRemaining(cell ->
                sheet.autoSizeColumn(cell.getColumnIndex())
        );
    }

    public static void groupColumns(Sheet sheet, int from, int to) {
        sheet.groupColumn(from, to);
        sheet.setColumnGroupCollapsed(from, true);
    }
}
