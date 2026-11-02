package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;

public record StoreMonthlyChecks(
        String storeId,
        String storeName,
        BigDecimal plannedAvgCheck,
        BigDecimal actualAvgCheck,
        BigDecimal actualAvgCheckWithLoyaltyCard,
        BigDecimal plannedAvgItemsPerCheck,
        BigDecimal actualAvgItemsPerCheck
) {
}
