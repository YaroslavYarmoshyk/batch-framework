package com.etake.avgcheckplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Constants {
    public static final MathContext PRECISE_MATH_CONTEXT = new MathContext(10, RoundingMode.HALF_UP);
    public static final String RESULT_SHEET = "result";
    public static final BigDecimal ADJUSTMENT_STRENGTH = new BigDecimal("0.2");
}
