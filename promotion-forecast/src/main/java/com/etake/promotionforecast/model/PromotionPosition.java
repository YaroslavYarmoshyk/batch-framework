package com.etake.promotionforecast.model;

import java.time.LocalDate;

public record PromotionPosition(
        String promotionId,
        String manager,
        String storeId,
        String storeName,
        String productId,
        String productName,
        String thirdSubcategoryId,
        String thirdSubcategoryName,
        Integer carryover,
        String storeFormat,
        LocalDate startDate,
        LocalDate endDate,
        Long soldUnits,
        Long daysOnStock
) {
}
