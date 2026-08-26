package com.etake.salesplan.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailySalesPlansRecord(
        String storeId,
        String categoryId,
        LocalDate date,
        BigDecimal turnover,
        BigDecimal margin
) {
}
