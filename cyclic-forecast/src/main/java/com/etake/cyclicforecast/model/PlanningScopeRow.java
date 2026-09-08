package com.etake.cyclicforecast.model;

import java.time.LocalDate;

public record PlanningScopeRow(
        String id,
        String promotionId,
        String storeId,
        String productId,
        String promotionType,
        String storeFormat,
        LocalDate startDate,
        Long carryover
) {
}
