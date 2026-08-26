package com.etake.salesplan.model;

import java.math.BigDecimal;

public record StoreCategorySalesRecord(
        String regionId,
        String storeId,
        String categoryId,
        String storeName,
        String categoryName,
        Integer dayOfMonth,
        BigDecimal turnover,
        BigDecimal margin
) {
}
