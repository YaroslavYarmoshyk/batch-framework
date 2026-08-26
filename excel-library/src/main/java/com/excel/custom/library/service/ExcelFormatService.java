package com.excel.custom.library.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;

public interface ExcelFormatService {

    CellStyle getHeaderStyle(final Workbook workbook, final IndexedColors indexedColors);

    CellStyle getHeaderStyle(final Workbook workbook);
}