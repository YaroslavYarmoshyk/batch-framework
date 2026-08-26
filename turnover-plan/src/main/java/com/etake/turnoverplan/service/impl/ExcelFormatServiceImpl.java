package com.etake.turnoverplan.service.impl;

import com.etake.turnoverplan.config.indices.ResultSheetColumnIndices;
import com.etake.turnoverplan.config.indices.StoresSheetColumnIndices;
import com.etake.turnoverplan.config.properties.ColumnIndices;
import com.etake.turnoverplan.service.ExcelFormatService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Color;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.etake.turnoverplan.utils.Constants.DATA_SHEET_FREEZE_COLUMN_NUMBER;
import static com.etake.turnoverplan.utils.Constants.FIRST_COLUMN_NUMBER;
import static com.etake.turnoverplan.utils.Constants.FIRST_ROW_INDEX;
import static com.etake.turnoverplan.utils.Constants.INITIAL_VALUE_ROW_INDEX;
import static com.etake.turnoverplan.utils.Constants.SECOND_ROW_INDEX;
import static com.etake.turnoverplan.utils.ExcelUtils.applyCellStyle;
import static com.etake.turnoverplan.utils.ExcelUtils.autosizeColumns;

@Service
@RequiredArgsConstructor
public class ExcelFormatServiceImpl implements ExcelFormatService {
    private static final XSSFColor GREY_COLOR = new XSSFColor(new byte[]{(byte) 38, (byte) 38, (byte) 38}, new DefaultIndexedColorMap());
    private static final XSSFColor LIGHT_GREY_COLOR = new XSSFColor(new byte[]{(byte) 64, (byte) 64, (byte) 64}, new DefaultIndexedColorMap());
    private static final XSSFColor LIGHT_GREEN_COLOR = new XSSFColor(new byte[]{(byte) 218, (byte) 242, (byte) 208}, new DefaultIndexedColorMap());
    private static final String DEFAULT_FONT = "Aptos Narrow";
    private static final String DEFAULT_NUMBER_FORMAT = "# ### ##0.0;-# ### ##0.0;-";
    private static final String DEFAULT_PERCENTAGE_FORMAT = "0.0%";
    private final ColumnIndices columnIndices;

    @Override
    public void formatDataSheet(final Workbook workbook, final Sheet sheet) {
        formatHeaders(workbook, sheet, columnIndices.dataSheet());
        formatValues(workbook, sheet);

        final DataFormat dataFormat = workbook.createDataFormat();
        final CellStyle numberCellStyle = getDefaultCellStyle(workbook);
        numberCellStyle.setDataFormat(dataFormat.getFormat(DEFAULT_NUMBER_FORMAT));
        final CellStyle percentageCellStyle = getDefaultCellStyle(workbook);
        percentageCellStyle.setDataFormat(dataFormat.getFormat(DEFAULT_PERCENTAGE_FORMAT));

        final int endRowIndex = sheet.getLastRowNum() - 1;
        final Set<Integer> numberFormatIndices = columnIndices.dataSheet().getNumberFormatIndices();
        numberFormatIndices.forEach(i -> applyCellStyle(sheet, numberCellStyle, INITIAL_VALUE_ROW_INDEX, i, endRowIndex, i));
        columnIndices.dataSheet().getPercentageFormatIndices().forEach(i -> applyCellStyle(sheet, percentageCellStyle, INITIAL_VALUE_ROW_INDEX, i, endRowIndex, i));

        sheet.createFreezePane(DATA_SHEET_FREEZE_COLUMN_NUMBER, INITIAL_VALUE_ROW_INDEX);

        autosizeColumns(sheet);
    }

    @Override
    public void formatCategoriesSheet(final Workbook workbook, final Sheet sheet) {
        formatHeaders(workbook, sheet, columnIndices.categoriesSheet());

        final short dataFormat = workbook.createDataFormat().getFormat(DEFAULT_NUMBER_FORMAT);
        formatValues(workbook, sheet, dataFormat);
        formatTotalRow(workbook, sheet, dataFormat);

        sheet.createFreezePane(FIRST_COLUMN_NUMBER, INITIAL_VALUE_ROW_INDEX);

        autosizeColumns(sheet);
    }

    @Override
    public void formatStoresSheet(final Workbook workbook, final Sheet sheet, final Collection<String> regions) {
        final StoresSheetColumnIndices storesSheetColumnIndices = columnIndices.storesSheet();
        final Set<Integer> saleIndicatorIndices = new HashSet<>(storesSheetColumnIndices.getTurnoverColumnIndices());
        saleIndicatorIndices.addAll(storesSheetColumnIndices.getMarginColumnIndices());
        saleIndicatorIndices.add(storesSheetColumnIndices.adjustedPlanTurnoverFirstMonth());
        saleIndicatorIndices.add(storesSheetColumnIndices.adjustedPlanMarginFirstMonth());
        formatHeaders(workbook, sheet, saleIndicatorIndices);

        final short dataFormat = workbook.createDataFormat().getFormat(DEFAULT_NUMBER_FORMAT);
        formatValues(workbook, sheet, storesSheetColumnIndices.adjustedPlanTurnoverFirstMonth(), storesSheetColumnIndices.adjustedPlanMarginFirstMonth(), dataFormat);
        formatRegionRows(workbook, sheet, regions, dataFormat);
        formatTotalRow(workbook, sheet, dataFormat);

        sheet.createFreezePane(FIRST_COLUMN_NUMBER, INITIAL_VALUE_ROW_INDEX);

        autosizeColumns(sheet);
        sheet.setColumnWidth(storesSheetColumnIndices.adjustedPlanTurnoverFirstMonth(), sheet.getColumnWidth(storesSheetColumnIndices.adjustedPlanMarginFirstMonth()));
    }

    private static void formatHeaders(final Workbook workbook, final Sheet sheet, final ResultSheetColumnIndices resultSheetColumnIndices) {
        final Set<Integer> saleIndicatorIndices = new HashSet<>(resultSheetColumnIndices.getTurnoverColumnIndices());
        saleIndicatorIndices.addAll(resultSheetColumnIndices.getMarginColumnIndices());
        formatHeaders(workbook, sheet, saleIndicatorIndices);
    }

    private static void formatHeaders(final Workbook workbook, final Sheet sheet, final Set<Integer> saleIndicatorIndices) {
        final Row firstRow = sheet.getRow(FIRST_ROW_INDEX);
        final short defaultHeight = firstRow.getHeight();
        firstRow.setHeight((short) (defaultHeight * 2));
        final Row secondRow = sheet.getRow(SECOND_ROW_INDEX);
        secondRow.setHeight(defaultHeight);
        final CellStyle greyHeaderCellStyle = getHeaderCellStyle(workbook, GREY_COLOR);
        firstRow.cellIterator().forEachRemaining(cell -> cell.setCellStyle(greyHeaderCellStyle));

        final CellStyle lightGreyHeaderCellStyle = getHeaderCellStyle(workbook, LIGHT_GREY_COLOR);
        saleIndicatorIndices.forEach(i -> secondRow.getCell(i).setCellStyle(lightGreyHeaderCellStyle));
    }

    private static void formatValues(final Workbook workbook, final Sheet sheet) {
        formatValueRange(sheet, getDefaultCellStyle(workbook));
    }

    private static void formatValues(final Workbook workbook, final Sheet sheet, final short dataFormat) {
        final CellStyle defaultCellStyle = getDefaultCellStyle(workbook);
        defaultCellStyle.setDataFormat(dataFormat);
        formatValueRange(sheet, defaultCellStyle);
    }

    private static void formatValues(final Workbook workbook, final Sheet sheet,
                                     final Integer fromAdjustedColumnIndex, final Integer toAdjustedColumnIndex,
                                     final short dataFormat) {
        final CellStyle defaultCellStyle = getDefaultCellStyle(workbook);
        defaultCellStyle.setDataFormat(dataFormat);

        final CellStyle adjustedValuesCellStyle = getDefaultCellStyle(workbook);
        adjustedValuesCellStyle.setFillForegroundColor(LIGHT_GREEN_COLOR);
        adjustedValuesCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        adjustedValuesCellStyle.setDataFormat(dataFormat);
        formatValueRange(sheet, defaultCellStyle);
        applyCellStyle(sheet, adjustedValuesCellStyle, INITIAL_VALUE_ROW_INDEX, fromAdjustedColumnIndex, sheet.getLastRowNum(), toAdjustedColumnIndex);
    }

    private static void formatRegionRows(final Workbook workbook, final Sheet sheet, final Collection<String> regions, final short dataFormat) {
        final short firstCellIndex = sheet.getRow(FIRST_ROW_INDEX).getFirstCellNum();
        final Set<Integer> regionRowIndices = IntStream.range(sheet.getTopRow(), sheet.getLastRowNum())
                .filter(i -> regions.contains(sheet.getRow(i).getCell(firstCellIndex).getStringCellValue()))
                .boxed()
                .collect(Collectors.toSet());
        final CellStyle cellStyle = getHeaderCellStyleWithoutAlignment(workbook, LIGHT_GREY_COLOR);
        cellStyle.setDataFormat(dataFormat);

        for (final Integer index : regionRowIndices) {
            sheet.getRow(index).cellIterator().forEachRemaining(cell -> cell.setCellStyle(cellStyle));
        }
    }

    private static void formatTotalRow(final Workbook workbook, final Sheet sheet, final short dataFormat) {
        final CellStyle cellStyle = getHeaderCellStyle(workbook, GREY_COLOR);
        cellStyle.setDataFormat(dataFormat);
        final int totalRowIndex = sheet.getLastRowNum();
        sheet.getRow(totalRowIndex).cellIterator().forEachRemaining(cell -> cell.setCellStyle(cellStyle));
    }

    private static void formatValueRange(final Sheet sheet, final CellStyle defaultCellStyle) {
        final int endRowIndex = sheet.getLastRowNum() - 1;
        final int startColumnIndex = 0;
        final int endColumnIndex = sheet.getRow(FIRST_ROW_INDEX).getLastCellNum() - 1;
        applyCellStyle(sheet, defaultCellStyle, INITIAL_VALUE_ROW_INDEX, startColumnIndex, endRowIndex, endColumnIndex);
    }

    private static CellStyle getHeaderCellStyle(final Workbook workbook,
                                                final Color backgroundColor) {
        final CellStyle cellStyle = getHeaderCellStyleWithoutAlignment(workbook, backgroundColor);
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        return cellStyle;
    }

    private static CellStyle getHeaderCellStyleWithoutAlignment(final Workbook workbook,
                                                                final Color backgroundColor) {
        final CellStyle cellStyle = getDefaultCellStyle(workbook);
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cellStyle.setFillForegroundColor(backgroundColor);
        cellStyle.setFont(getFontByColor(workbook, IndexedColors.WHITE));
        cellStyle.setWrapText(true);
        return cellStyle;
    }

    private static CellStyle getDefaultCellStyle(final Workbook workbook) {
        final CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        return cellStyle;
    }


    private static Font getFontByColor(final Workbook workbook, final IndexedColors color) {
        final Font font = workbook.createFont();
        font.setFontName(DEFAULT_FONT);
        font.setColor(color.getIndex());
        font.setBold(true);
        return font;
    }
}
