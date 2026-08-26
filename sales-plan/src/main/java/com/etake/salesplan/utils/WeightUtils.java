package com.etake.salesplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WeightUtils {

    public static Map<String, BigDecimal> normalizeWeights(Map<String, BigDecimal> targets) {
        BigDecimal total = targets.values().stream().reduce(BigDecimal.ZERO, (a, b) -> a.add(b, PRECISE_MATH_CONTEXT));
        Map<String, BigDecimal> w = new LinkedHashMap<>();
        if (total.signum() == 0) {
            int n = targets.size();
            BigDecimal u = (n == 0) ? BigDecimal.ZERO : BigDecimal.ONE.divide(BigDecimal.valueOf(n), PRECISE_MATH_CONTEXT);
            for (String k : targets.keySet()) w.put(k, u);
            return w;
        }
        for (Map.Entry<String, BigDecimal> e : targets.entrySet()) {
            w.put(e.getKey(), e.getValue().divide(total, PRECISE_MATH_CONTEXT));
        }
        return w;
    }
}
