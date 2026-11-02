package com.etake.promotionforecast.writer.googlesheets;

import com.google.api.services.sheets.v4.model.AppendDimensionRequest;
import com.google.api.services.sheets.v4.model.BasicFilter;
import com.google.api.services.sheets.v4.model.BooleanCondition;
import com.google.api.services.sheets.v4.model.Border;
import com.google.api.services.sheets.v4.model.Borders;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ColorStyle;
import com.google.api.services.sheets.v4.model.ConditionValue;
import com.google.api.services.sheets.v4.model.CopyPasteRequest;
import com.google.api.services.sheets.v4.model.DataValidationRule;
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.DimensionProperties;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.DuplicateSheetRequest;
import com.google.api.services.sheets.v4.model.ExtendedValue;
import com.google.api.services.sheets.v4.model.GridProperties;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.NumberFormat;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.SetBasicFilterRequest;
import com.google.api.services.sheets.v4.model.SetDataValidationRequest;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.model.UpdateBordersRequest;
import com.google.api.services.sheets.v4.model.UpdateCellsRequest;
import com.google.api.services.sheets.v4.model.UpdateDimensionPropertiesRequest;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Factory of Sheets API {@code batchUpdate} requests. Rows and columns are 0-based; a {@code null} end index means
 * "to the end of the sheet".
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SheetRequests {
    private static final String CLEAR_FIELDS = "userEnteredValue,userEnteredFormat";
    private static final ColorStyle BLACK = rgb(0, 0, 0);
    private static final ColorStyle WHITE = rgb(0xff, 0xff, 0xff);

    public static GridRange range(final int sheetId, final int row, final int rows, final int column, final int columns) {
        return new GridRange()
                .setSheetId(sheetId)
                .setStartRowIndex(row).setEndRowIndex(row + rows)
                .setStartColumnIndex(column).setEndColumnIndex(column + columns);
    }

    /** Columns {@code [fromColumn, toColumn)} from {@code row} to the end of the sheet. */
    public static GridRange openRange(final int sheetId, final int row, final int fromColumn, final Integer toColumn) {
        return new GridRange().setSheetId(sheetId).setStartRowIndex(row)
                .setStartColumnIndex(fromColumn).setEndColumnIndex(toColumn);
    }

    public static Request repeat(final GridRange range, final CellData cell, final String fields) {
        return new Request().setRepeatCell(new RepeatCellRequest().setRange(range).setCell(cell).setFields(fields));
    }

    /** The formula is written for the first row of the range; RepeatCell shifts its relative references for every row. */
    public static Request fillFormula(final GridRange range, final String formula) {
        return repeat(range, new CellData().setUserEnteredValue(new ExtendedValue().setFormulaValue(formula)), "userEnteredValue");
    }

    public static Request fillNumber(final GridRange range, final double value) {
        return repeat(range, new CellData().setUserEnteredValue(new ExtendedValue().setNumberValue(value)), "userEnteredValue");
    }

    public static Request numberFormat(final GridRange range, final NumberFormat format) {
        return repeat(range, new CellData().setUserEnteredFormat(new CellFormat().setNumberFormat(format)),
                "userEnteredFormat.numberFormat");
    }

    public static Request inputFormat(final GridRange range, final NumberFormat format, final ColorStyle background) {
        return repeat(range, new CellData().setUserEnteredFormat(new CellFormat()
                        .setNumberFormat(format)
                        .setBackgroundColorStyle(background)),
                "userEnteredFormat(numberFormat,backgroundColorStyle)");
    }

    /** Clears values and formatting; data validation (dropdowns) is left as is. */
    public static Request clear(final GridRange range) {
        return repeat(range, new CellData(), CLEAR_FIELDS);
    }

    public static Request clearValues(final GridRange range) {
        return repeat(range, new CellData(), "userEnteredValue");
    }

    public static Request texts(final int sheetId, final int row, final int column, final List<String> texts) {
        final List<CellData> cells = texts.stream()
                .map(text -> new CellData().setUserEnteredValue(new ExtendedValue().setStringValue(text)))
                .toList();
        return new Request().setUpdateCells(new UpdateCellsRequest()
                .setRange(range(sheetId, row, 1, column, texts.size()))
                .setRows(List.of(new RowData().setValues(cells)))
                .setFields("userEnteredValue"));
    }

    /** Bold white centred wrapped text on the given background, with thin borders. */
    public static Request headerFormat(final GridRange range, final ColorStyle background) {
        final Border border = new Border().setStyle("SOLID").setColorStyle(BLACK);
        return repeat(range, new CellData().setUserEnteredFormat(new CellFormat()
                        .setBackgroundColorStyle(background)
                        .setTextFormat(new TextFormat().setBold(true).setForegroundColorStyle(WHITE))
                        .setHorizontalAlignment("CENTER")
                        .setVerticalAlignment("MIDDLE")
                        .setWrapStrategy("WRAP")
                        .setBorders(new Borders().setTop(border).setBottom(border).setLeft(border).setRight(border))),
                "userEnteredFormat(backgroundColorStyle,textFormat,horizontalAlignment,verticalAlignment,wrapStrategy,borders)");
    }

    public static Request borders(final GridRange range) {
        final Border border = new Border().setStyle("SOLID").setColorStyle(BLACK);
        return new Request().setUpdateBorders(new UpdateBordersRequest()
                .setRange(range)
                .setTop(border).setBottom(border).setLeft(border).setRight(border)
                .setInnerHorizontal(border).setInnerVertical(border));
    }

    /** Copies the source cell's dropdown (with its chip colours) onto every cell of the destination. */
    public static Request copyValidation(final GridRange source, final GridRange destination) {
        return new Request().setCopyPaste(new CopyPasteRequest()
                .setSource(source)
                .setDestination(destination)
                .setPasteType("PASTE_DATA_VALIDATION"));
    }

    /** Plain dropdown with the given values, for when the template has none to copy. */
    public static Request listValidation(final GridRange range, final List<String> values) {
        return new Request().setSetDataValidation(new SetDataValidationRequest()
                .setRange(range)
                .setRule(new DataValidationRule()
                        .setCondition(new BooleanCondition()
                                .setType("ONE_OF_LIST")
                                .setValues(values.stream().map(value -> new ConditionValue().setUserEnteredValue(value)).toList()))
                        .setStrict(true)
                        .setShowCustomUi(true)));
    }

    public static Request clearValidation(final GridRange range) {
        return new Request().setSetDataValidation(new SetDataValidationRequest().setRange(range));
    }

    public static Request appendRows(final int sheetId, final int count) {
        return new Request().setAppendDimension(new AppendDimensionRequest().setSheetId(sheetId).setDimension("ROWS").setLength(count));
    }

    public static Request appendColumns(final int sheetId, final int count) {
        return new Request().setAppendDimension(new AppendDimensionRequest().setSheetId(sheetId).setDimension("COLUMNS").setLength(count));
    }

    public static Request deleteColumns(final int sheetId, final int fromColumn, final int toColumn) {
        return new Request().setDeleteDimension(new DeleteDimensionRequest()
                .setRange(new DimensionRange().setSheetId(sheetId).setDimension("COLUMNS").setStartIndex(fromColumn).setEndIndex(toColumn)));
    }

    public static Request columnWidth(final int sheetId, final int column, final int pixels) {
        return dimensionSize(sheetId, "COLUMNS", column, pixels);
    }

    public static Request rowHeight(final int sheetId, final int row, final int pixels) {
        return dimensionSize(sheetId, "ROWS", row, pixels);
    }

    public static Request basicFilter(final GridRange range) {
        return new Request().setSetBasicFilter(new SetBasicFilterRequest().setFilter(new BasicFilter().setRange(range)));
    }

    public static Request frozenColumns(final int sheetId, final int count) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId)
                        .setGridProperties(new GridProperties().setFrozenColumnCount(count)))
                .setFields("gridProperties.frozenColumnCount"));
    }

    public static Request hide(final int sheetId) {
        return new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest()
                .setProperties(new SheetProperties().setSheetId(sheetId).setHidden(true))
                .setFields("hidden"));
    }

    public static Request deleteSheet(final int sheetId) {
        return new Request().setDeleteSheet(new DeleteSheetRequest().setSheetId(sheetId));
    }

    public static Request duplicateSheet(final int sourceSheetId, final int newSheetId, final String title, final int index) {
        return new Request().setDuplicateSheet(new DuplicateSheetRequest()
                .setSourceSheetId(sourceSheetId)
                .setNewSheetId(newSheetId)
                .setNewSheetName(title)
                .setInsertSheetIndex(index));
    }

    public static ColorStyle rgb(final int red, final int green, final int blue) {
        return new ColorStyle().setRgbColor(new Color().setRed(red / 255f).setGreen(green / 255f).setBlue(blue / 255f));
    }

    private static Request dimensionSize(final int sheetId, final String dimension, final int index, final int pixels) {
        return new Request().setUpdateDimensionProperties(new UpdateDimensionPropertiesRequest()
                .setRange(new DimensionRange().setSheetId(sheetId).setDimension(dimension).setStartIndex(index).setEndIndex(index + 1))
                .setProperties(new DimensionProperties().setPixelSize(pixels))
                .setFields("pixelSize"));
    }
}
