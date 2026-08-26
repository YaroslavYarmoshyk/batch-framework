package com.excel.custom.library.util;

import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

public final class Constants {
    public static final int DEFAULT_AVERAGE_SALES_SCALE = 2;
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    public static final String DEFAULT_FONT_NAME = "Calibri";
    public static final short DEFAULT_FONT_SIZE = 11;
    public static final DateTimeFormatter DEFAULT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
}