package com.etake.cyclicforecast.model;

import java.math.BigDecimal;

public record ReportRow(
        String manager,
        String store,
        String storeFormat,
        String productId,
        String productName,
        String subcategory,
        String promotionType,
        Long carryover,
        String algorithmLevel,
        String donorKey,
        BigDecimal avgDailySales,
        Long forecastQuantity
) {
}
