package com.etake.salesplan.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record NewStoreCandidate(
        String regionId,
        String storeId,
        String storeName,
        LocalDate openDate,
        BigDecimal turnover,
        BigDecimal margin,
        Integer tradingDays
) {
}
