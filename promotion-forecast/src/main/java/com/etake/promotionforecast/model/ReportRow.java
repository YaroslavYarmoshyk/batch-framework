package com.etake.promotionforecast.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportRow(
        String manager,
        String storeName,
        String storeFormat,
        String productId,
        String productName,
        String subcategory,
        String promotionType,
        LocalDate startDate,
        LocalDate endDate,
        Integer carryover,
        int algorithmLevel,
        String donorStore,
        String donorFormat,
        LocalDate donorStartDate,
        LocalDate donorEndDate,
        Long donorSoldUnits,
        Long donorDaysOnStock,
        BigDecimal avgDailySales,
        Long forecastQuantity
) {
}
