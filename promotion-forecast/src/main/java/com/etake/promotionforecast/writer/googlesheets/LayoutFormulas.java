package com.etake.promotionforecast.writer.googlesheets;

import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.ForecastColumn;
import com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TotalColumn;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.FIRST_DATA_ROW;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.FORECAST_SHEET;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.TOTAL_SHEET;
import static com.etake.promotionforecast.writer.googlesheets.PromotionSheetLayout.letter;

/**
 * Formulas of the promotion spreadsheet, written for the first data row. They are filled down with a
 * {@code RepeatCellRequest}, which shifts the relative references for every following row.
 *
 * <p>{@code USER_ENTERED} formulas are parsed in the spreadsheet locale, so the argument separator follows it:
 * {@code ;} where the decimal separator is a comma (e.g. uk_UA, ru_RU), {@code ,} otherwise.
 */
public final class LayoutFormulas {
    private static final String YES = "\"Так\"";
    private static final String NO = "\"Ні\"";
    private static final String EMPTY = "\"\"";
    private static final String FIRST_SHIPMENT_SHARE = "70%";
    private static final String FULL_SHARE = "100%";

    private final String separator;

    LayoutFormulas(final String separator) {
        this.separator = separator;
    }

    public static LayoutFormulas forLocale(final String sheetsLocale) {
        final Locale locale = sheetsLocale == null ? Locale.US : Locale.forLanguageTag(sheetsLocale.replace('_', '-'));
        final boolean decimalComma = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator() == ',';
        return new LayoutFormulas(decimalComma ? ";" : ",");
    }

    public String separator() {
        return separator;
    }

    /** Row's share of the product forecast × «Корегований прогноз». */
    public String adjustedQty() {
        final String productTotal = call("SUMIF",
                column(TotalColumn.PRODUCT_ID), cell(TotalColumn.PRODUCT_ID), column(TotalColumn.FORECAST_QUANTITY));
        return "=" + call("IFERROR",
                cell(TotalColumn.FORECAST_QUANTITY) + "/" + productTotal + "*" + forecastLookup(ForecastColumn.ADJUSTED_FORECAST),
                EMPTY);
    }

    /** «Загальна акція»: quantity from the manager sheet, or the forecast when the manager sheet is missing. */
    public String managerQty() {
        return "=" + call("IFERROR",
                call("SUMIFS", managerColumn(TotalColumn.MANAGER_QTY), sameProductAndStore()),
                cell(TotalColumn.FORECAST_QUANTITY));
    }

    /** «Загальна акція»: «Так» when the manager marked the row with «Так» in the given column. */
    public String managerFlag(final TotalColumn column) {
        return "=" + call("IF",
                call("COUNTIFS", managerColumn(column), YES, sameProductAndStore()) + "=1",
                YES, NO);
    }

    /** «Загальна акція»: 70% when the manager kept the first shipment at 70%, otherwise 100%. */
    public String firstShipment() {
        return "=" + call("IF",
                call("COUNTIFS", managerColumn(TotalColumn.FIRST_SHIPMENT), FIRST_SHIPMENT_SHARE, sameProductAndStore()) + "=1",
                FIRST_SHIPMENT_SHARE, FULL_SHARE);
    }

    public String multiplicity() {
        return "=" + call("IFERROR", forecastLookup(ForecastColumn.MULTIPLICITY), EMPTY);
    }

    /** «Прогноз»: product forecast summed over «Загальна акція». */
    public String analystForecast() {
        final String total = SheetCells.quote(TOTAL_SHEET) + "!";
        return "=" + call("SUMIF",
                total + column(TotalColumn.PRODUCT_ID),
                forecastCell(ForecastColumn.CODE),
                total + column(TotalColumn.FORECAST_QUANTITY));
    }

    /** «Прогноз»: analyst forecast against the manager forecast. */
    public String deviation() {
        return "=" + call("IFERROR",
                forecastCell(ForecastColumn.ANALYST_FORECAST) + "/" + forecastCell(ForecastColumn.MANAGER_FORECAST) + "-1",
                EMPTY);
    }

    private String forecastLookup(final ForecastColumn column) {
        final String range = SheetCells.quote(FORECAST_SHEET) + "!A:" + letter(column.index());
        return call("VLOOKUP", cell(TotalColumn.PRODUCT_ID), range, String.valueOf(column.index() + 1), "0");
    }

    private String sameProductAndStore() {
        return String.join(separator,
                managerColumn(TotalColumn.PRODUCT_ID), cell(TotalColumn.PRODUCT_ID),
                managerColumn(TotalColumn.STORE), cell(TotalColumn.STORE));
    }

    /** Whole column of the row's manager sheet; apostrophes in names (Мар'яна) are escaped for the quoted sheet name. */
    private String managerColumn(final TotalColumn column) {
        final String manager = call("SUBSTITUTE", cell(TotalColumn.MANAGER), "\"'\"", "\"''\"");
        return call("INDIRECT", "\"'\"&" + manager + "&\"'!" + column(column) + "\"");
    }

    private String call(final String function, final String... arguments) {
        return function + "(" + String.join(separator, arguments) + ")";
    }

    private static String cell(final TotalColumn column) {
        return column.letter() + (FIRST_DATA_ROW + 1);
    }

    private static String forecastCell(final ForecastColumn column) {
        return letter(column.index()) + (FIRST_DATA_ROW + 1);
    }

    private static String column(final TotalColumn column) {
        return column.letter() + ":" + column.letter();
    }
}
