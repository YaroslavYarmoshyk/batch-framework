package com.etake.salesplan.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.MathContext;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CalculationConstants {

    public static final MathContext PRECISE_MATH_CONTEXT = MathContext.DECIMAL128;
}
