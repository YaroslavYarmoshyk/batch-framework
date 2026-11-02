package com.etake.coresheetrenewal.model;

import java.util.List;

public record StorePerformance(
        String storeId,
        String storeName,
        StoreMonthlyChecks checks,
        List<StoreDailySales> sales
) {
}
