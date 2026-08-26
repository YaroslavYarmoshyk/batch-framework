package com.etake.turnoverplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class Constants {
    public static final String DATA_SHEET_NAME = "data";
    public static final String CATEGORY_SHEET_NAME = "План по категоріям";
    public static final String STORES_SHEET_NAME = "План по магазинам";
    public static final String STORE = "Магазин";
    public static final String SIMILAR_STORE = "Схожий магазин";
    public static final String CATEGORY = "Категорія";
    public static final String TOTAL = "Разом";
    public static final String TURNOVER_COLUMN_NAME = "ТО";
    public static final String MARGIN_COLUMN_NAME = "Маржа";
    public static final Integer FIRST_ROW_INDEX = 0;
    public static final Integer SECOND_ROW_INDEX = 1;
    public static final Integer INITIAL_VALUE_ROW_INDEX = 2;
    public static final Integer FIRST_COLUMN_NUMBER = 1;
    public static final Integer DATA_SHEET_FREEZE_COLUMN_NUMBER = 3;
    public static final Integer DEFAULT_COLUMN_WIDTH = 5;

    public static final Map<String, Integer> COLUMN_WIDTH = Map.of(
            STORE, 22,
            SIMILAR_STORE, 22,
            CATEGORY, 31,
            TURNOVER_COLUMN_NAME, 12,
            MARGIN_COLUMN_NAME, 12
    );
}
