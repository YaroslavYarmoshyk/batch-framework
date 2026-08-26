package com.etake.salesplan.constants;

import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFColor;

public class ExcelConstants {
    public static final Integer FIRST_ROW_INDEX = 0;
    public static final Integer SECOND_ROW_INDEX = 1;
    public static final Integer INITIAL_VALUE_ROW_INDEX = 2;
    public static final Integer MAX_DATA_SHEET_COLUMN_INDEX = 5;

    public static final String STORE = "Магазин";
    public static final String SIMILAR_STORE = "Схожий магазин";
    public static final String CATEGORY = "Категорія";
    public static final String DATE = "Дата";
    public static final String TURNOVER = "ТО";
    public static final String MARGIN = "Маржа";
    public static final String TURNOVER_PLAN = "План ТО";
    public static final String MARGIN_PLAN = "План Маржа";
    public static final String DEFAULT_FONT = "Aptos Narrow";
    public static final XSSFColor GREY_COLOR = new XSSFColor(new byte[]{(byte) 38, (byte) 38, (byte) 38}, new DefaultIndexedColorMap());
    public static final XSSFColor LIGHT_GREY_COLOR = new XSSFColor(new byte[]{(byte) 64, (byte) 64, (byte) 64}, new DefaultIndexedColorMap());
    public static final XSSFColor LIGHT_GREEN_COLOR = new XSSFColor(new byte[]{(byte) 218, (byte) 242, (byte) 208}, new DefaultIndexedColorMap());
}
