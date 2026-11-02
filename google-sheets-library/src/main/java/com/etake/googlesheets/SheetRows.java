package com.etake.googlesheets;

import java.util.List;

/**
 * A block of rows to write into a sheet, starting at column A of {@code startRow} (1-based).
 */
public record SheetRows(String sheetName, int startRow, List<List<Object>> rows) {
}
