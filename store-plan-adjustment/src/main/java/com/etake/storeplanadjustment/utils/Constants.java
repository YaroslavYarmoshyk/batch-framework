package com.etake.storeplanadjustment.utils;

import java.math.MathContext;

public final class Constants {
    public static final MathContext PRECISE_MATH_CONTEXT = MathContext.DECIMAL64;

    public static final String RESULT_SHEET = "Adjusted plans";
    public static final String TURNOVER_FORMAT = "0.00";

    /** Number of 30-minute slots in a day. */
    public static final int SLOTS_PER_DAY = 48;

    /** Length of one intraday slot, in minutes. */
    public static final int SLOT_MINUTES = 30;

    /** How many recent same-weekday dates form an intraday share profile. */
    public static final int PROFILE_WEEKS = 4;

    private Constants() {
    }
}
