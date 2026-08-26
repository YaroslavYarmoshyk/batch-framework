package com.excel.custom.library.service.impl;

import org.springframework.stereotype.Service;
import com.excel.custom.library.service.ExcelFormatService;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static com.excel.custom.library.util.Constants.DEFAULT_FONT_NAME;
import static com.excel.custom.library.util.Constants.DEFAULT_FONT_SIZE;

@Service
public class ExcelFormatServiceImpl implements ExcelFormatService {
    @Override
    public CellStyle getHeaderStyle(final Workbook workbook, final IndexedColors indexedColors) {
        final CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        cellStyle.setWrapText(true);
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cellStyle.setBorderBottom(BorderStyle.MEDIUM);
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        cellStyle.setFillForegroundColor(indexedColors.getIndex());

        final XSSFFont font = getFont(workbook, IndexedColors.WHITE, true);
        cellStyle.setFont(font);

        return cellStyle;
    }

    @Override
    public CellStyle getHeaderStyle(final Workbook workbook) {
        return getHeaderStyle(workbook, IndexedColors.GREY_80_PERCENT);
    }

    private XSSFFont getFont(final Workbook workbook,
                             final IndexedColors indexedColor,
                             final boolean isBold) {
        final XSSFFont font = ((XSSFWorkbook) workbook).createFont();
        font.setFontName(DEFAULT_FONT_NAME);
        font.setFontHeightInPoints(DEFAULT_FONT_SIZE);
        font.setColor(indexedColor.getIndex());
        font.setBold(isBold);
        return font;
    }
}