package com.etake.promotionforecast.writer.googlesheets;

import com.etake.promotionforecast.model.ReportColumn;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Layout of the promotion spreadsheet. «Загальна акція» and every manager sheet share the {@link TotalColumn}s:
 * forecast data first, then the columns managers work with. All indexes are 0-based.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromotionSheetLayout {
    public static final String TOTAL_SHEET = "Загальна акція";
    public static final String FORECAST_SHEET = "Прогноз";
    public static final String EXPORT_SHEET = "Заливка в 1С";
    /** Sheets the rebuild never deletes, in addition to the configured source sheet. {@code data} is the legacy source. */
    public static final Set<String> PERMANENT_SHEETS = Set.of(TOTAL_SHEET, FORECAST_SHEET, EXPORT_SHEET, "Менеджери", "data");

    public static final int HEADER_ROW = 0;
    public static final int FIRST_DATA_ROW = 1;
    public static final int WIDTH = TotalColumn.values().length;
    public static final int HEADER_HEIGHT_PX = 50;
    /** Store and product stay visible while scrolling: columns A–G (up to «Номенклатура»). */
    public static final int FROZEN_COLUMNS = TotalColumn.PRODUCT_NAME.index() + 1;

    /** Dropdown values of the manager's yes/no columns. */
    public static final List<String> YES_NO = List.of("Так", "Ні");
    /** Dropdown values of «Перша відправка, %». */
    public static final List<String> SHIPMENT_SHARES = List.of("100%", "70%");

    /**
     * Columns of «Загальна акція» and the manager sheets, in sheet order. Widths and header colours follow the
     * cyclic-action template: grey headers for the analyst's columns, green for the ones managers work with.
     */
    @Getter
    @RequiredArgsConstructor
    public enum TotalColumn {
        MANAGER("Менеджер", ReportColumn.MANAGER, 21, false),
        STORE("Магазин", ReportColumn.STORE, 181, false),
        STORE_FORMAT("Формат", ReportColumn.STORE_FORMAT, 28, false),
        CARRYOVER("Перехідний залишок", ReportColumn.CARRYOVER, 28, false),
        SUBCATEGORY("Підкатегорія3", ReportColumn.SUBCATEGORY, 28, false),
        PRODUCT_ID("Код", ReportColumn.PRODUCT_ID, 59, false),
        PRODUCT_NAME("Номенклатура", ReportColumn.PRODUCT_NAME, 227, false),
        ALGORITHM_LEVEL("Алгоритм", ReportColumn.ALGORITHM_LEVEL, 35, false),
        AVG_DAILY_SALES("СДП прогнозовані", ReportColumn.AVG_DAILY_SALES, 35, false),
        FORECAST_QUANTITY("Прогнозовані продажі", ReportColumn.FORECAST_QUANTITY, 58, false),
        /** Row's share of the product forecast × «Корегований прогноз» from «Прогноз». */
        ADJUSTED_QTY("Прогнозовані продажі кореговані", null, 134, false),
        /** Manager's quantity; on «Загальна акція» it falls back to the forecast. */
        MANAGER_QTY("Прогнозовані продажі менеджер", null, 101, true),
        /** Dropdown «Так» / «Ні». */
        INCLUDE_STOCK("Чи враховувати залишок на магазині", null, 114, true),
        /** «Кратність» from «Прогноз». */
        MULTIPLICITY("Кратність", null, 91, true),
        /** Dropdown «Так» / «Ні». */
        INCLUDE_MULTIPLICITY("Чи враховувати кратність на магазині", null, 114, true),
        /** Dropdown 100% / 70%. */
        FIRST_SHIPMENT("Перша відправка, %", null, 91, true);

        /** Columns filled with forecast values; the rest are formulas or manager input. */
        public static final List<TotalColumn> DATA = Arrays.stream(values()).filter(TotalColumn::isData).toList();

        private final String header;
        /** Report column the value comes from, {@code null} for formula / input columns. */
        private final ReportColumn source;
        private final int widthPx;
        /** Column managers work with (green header). */
        private final boolean managerColumn;

        public int index() {
            return ordinal();
        }

        public boolean isData() {
            return source != null;
        }

        /** Letter of the column in A1 notation. */
        public String letter() {
            return PromotionSheetLayout.letter(index());
        }
    }

    /** Columns of «Прогноз»: one row per product. */
    public enum ForecastColumn {
        CODE,
        NAME,
        /** «Прогноз аналітик»: product forecast summed over all stores. */
        ANALYST_FORECAST,
        /** «Прогноз менеджер»: manual input. */
        MANAGER_FORECAST,
        /** «Відхилення»: ANALYST_FORECAST / MANAGER_FORECAST − 1. */
        DEVIATION,
        /** «Корегований прогноз»: manual input, distributed over stores. */
        ADJUSTED_FORECAST,
        /** «Кратність»: manual input. */
        MULTIPLICITY;

        public int index() {
            return ordinal();
        }
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    public static String letter(final int index) {
        final StringBuilder letter = new StringBuilder();
        for (int n = index + 1; n > 0; n = (n - 1) / 26) {
            letter.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return letter.toString();
    }
}
