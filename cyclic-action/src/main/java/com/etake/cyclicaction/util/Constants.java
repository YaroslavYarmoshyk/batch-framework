package com.etake.cyclicaction.util;

import org.apache.logging.log4j.util.Strings;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Set;

public final class Constants {
    public static final String MANAGER = "Менеджер";
    public static final String CARRYOVER = "ПЗ";
    public static final String DYNAMIC_COEFFICIENT = "Кеф. динаміки СДП";
    public static final String FORECAST_SALES = "Прогнозовані продажі";
    public static final String ALGORITHM = "Алгоритм";
    public static final String NAME = "Номенклатура";
    public static final String STORE = "Магазин";
    public static final String ACTION_CODE = "Код";
    public static final String STORE_FORMAT = "Формат";
    public static final String THIRD_GROUP = "Підкатегорія3";
    public static final String ACTION_TYPE = "Вид акції";
    public static final String ACTION_START_DATE = "Дата початку дії";
    public static final String ACTION_END_DATE = "Дата закінчення дії";
    public static final String BEFORE_ACTION_START_DATE = "Доакційна дата початку";
    public static final String BEFORE_ACTION_END_DATE = "Доакційна дата кінця";
    public static final String ACTION_AVERAGE_SALES = "СДП акція";
    public static final String BEFORE_ACTION_AVERAGE_SALES = "СДП до акції";
    public static final String FORECAST_ACTION_AVERAGE_SALES = "СДП прогнозовані";
    public static final String ACTUAL_SALES_STORE = "Склад";
    public static final String ACTUAL_SALES_CODE = "Код номенклатури SKU";
    public static final String ACTUAL_AVERAGE_SALES = "СПД SKU";
    public static final String ACTUAL_AVERAGE_SALES_RESULT = "СПД теперішні";
    public static final String REDUNDANT_CHARACTERS_IN_STORE_NAME = " \\(торговий зал\\)";

    public static final Set<String> FRACTIONAL_COLUMN_NAMES = Set.of(
            BEFORE_ACTION_AVERAGE_SALES,
            ACTION_AVERAGE_SALES,
            ACTUAL_AVERAGE_SALES_RESULT,
            FORECAST_ACTION_AVERAGE_SALES,
            DYNAMIC_COEFFICIENT);

    public static final String[] ACTION_HISTORY_COLUMN_NAMES = {
            MANAGER,
            Strings.EMPTY,
            Strings.EMPTY,
            STORE,
            Strings.EMPTY,
            ACTION_CODE,
            NAME,
            STORE_FORMAT,
            CARRYOVER,
            THIRD_GROUP,
            ACTION_TYPE,
            ACTION_START_DATE,
            ACTION_END_DATE,
            BEFORE_ACTION_START_DATE,
            BEFORE_ACTION_END_DATE,
            ACTION_AVERAGE_SALES,
            BEFORE_ACTION_AVERAGE_SALES,
            Strings.EMPTY
    };

    public static final String[] ACTUAL_AVG_SALES_COLUMN_NAMES = {
            ACTUAL_SALES_STORE,
            Strings.EMPTY,
            Strings.EMPTY,
            Strings.EMPTY,
            Strings.EMPTY,
            Strings.EMPTY,
            ACTUAL_SALES_CODE,
            Strings.EMPTY,
            Strings.EMPTY,
            Strings.EMPTY,
            Strings.EMPTY,
            Strings.EMPTY,
            ACTUAL_AVERAGE_SALES
    };

    public static int DEMAND_DAYS = 14;
    public static int MAX_COEFFICIENT = 5;
    public static int DEFAULT_COEFFICIENT = 2;
    public static final int DEFAULT_AVERAGE_SALES_SCALE = 2;
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
    public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
}
