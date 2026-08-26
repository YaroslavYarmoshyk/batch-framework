package com.etake.turnoverplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.RoundingMode;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class CalculationUtils {
    public static final int DEFAULT_SCALE = 2;
    public static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;
}

