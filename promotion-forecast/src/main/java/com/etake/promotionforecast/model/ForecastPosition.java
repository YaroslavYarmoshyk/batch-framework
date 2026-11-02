package com.etake.promotionforecast.model;

import com.etake.promotionforecast.model.enumeration.AlgorithmLevel;

import java.math.BigDecimal;

public record ForecastPosition(
        PromotionPosition actualPosition,
        PromotionPosition alignedPosition,
        AlgorithmLevel algorithmLevel,
        BigDecimal averageDailySales,
        BigDecimal forecast
) {
}
