package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StoreDailySales(
        LocalDate date,
        String storeId,
        String storeName,
        BigDecimal plannedTurnover,
        BigDecimal actualTurnover,
        BigDecimal actualMargin
) {
}
