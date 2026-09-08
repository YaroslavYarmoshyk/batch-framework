package com.etake.cyclicforecast.util;

import java.time.format.DateTimeFormatter;

public final class Constants {
    private Constants() {
    }

    public static final String SHEET_NAME = "result";
    public static final String SUMMARY_SHEET_NAME = "summary";
    public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    public static final String NUMBER_FORMAT = "#,##0.00";

    public static final int MANAGER = 0;
    public static final int STORE = 1;
    public static final int STORE_FORMAT = 2;
    public static final int PRODUCT_ID = 3;
    public static final int PRODUCT_NAME = 4;
    public static final int SUBCATEGORY = 5;
    public static final int PROMOTION_TYPE = 6;
    public static final int CARRYOVER = 7;
    public static final int ALGORITHM_LEVEL = 8;
    public static final int DONOR_KEY = 9;
    public static final int AVG_DAILY_SALES = 10;
    public static final int FORECAST_QUANTITY = 11;
    public static final int COLUMN_COUNT = 12;

    public static final String[] HEADERS = {
            "Менеджер",
            "Магазин",
            "Формат",
            "Код",
            "Номенклатура",
            "Підкатегорія3",
            "Вид акції",
            "ПЗ",
            "Алгоритм",
            "Донор",
            "СДП прогноз",
            "Прогнозовані продажі"
    };
}
