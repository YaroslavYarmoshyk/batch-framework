package com.etake.promotionforecast.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.function.Function;

/**
 * Columns of the full report uploaded to the source Google Sheet ({@code result}).
 * Declaration order is the column order — {@link #ordinal()} is the zero-based column index.
 */
@Getter
@RequiredArgsConstructor
public enum ReportColumn {
    MANAGER("Менеджер", ReportRow::manager),
    STORE("Магазин", ReportRow::storeName),
    STORE_FORMAT("Формат", ReportRow::storeFormat),
    PRODUCT_ID("Код", ReportRow::productId),
    PRODUCT_NAME("Номенклатура", ReportRow::productName),
    SUBCATEGORY("Підкатегорія3", ReportRow::subcategory),
    PROMOTION_TYPE("Вид акції", ReportRow::promotionType),
    START_DATE("Початок", ReportRow::startDate),
    END_DATE("Кінець", ReportRow::endDate),
    CARRYOVER("ПЗ", ReportRow::carryover),
    ALGORITHM_LEVEL("Алгоритм", ReportRow::algorithmLevel),
    DONOR_STORE("Донор: магазин", ReportRow::donorStore),
    DONOR_FORMAT("Донор: формат", ReportRow::donorFormat),
    DONOR_START_DATE("Донор: початок", ReportRow::donorStartDate),
    DONOR_END_DATE("Донор: кінець", ReportRow::donorEndDate),
    DONOR_SOLD_UNITS("Донор: продажі", ReportRow::donorSoldUnits),
    DONOR_DAYS_ON_STOCK("Донор: днів на залишку", ReportRow::donorDaysOnStock),
    AVG_DAILY_SALES("СДП прогноз", ReportRow::avgDailySales),
    FORECAST_QUANTITY("Прогнозовані продажі", ReportRow::forecastQuantity);

    public static final int COUNT = values().length;

    private final String header;
    private final Function<ReportRow, Object> extractor;

    public Object extract(final ReportRow row) {
        return extractor.apply(row);
    }

    public int index() {
        return ordinal();
    }
}
