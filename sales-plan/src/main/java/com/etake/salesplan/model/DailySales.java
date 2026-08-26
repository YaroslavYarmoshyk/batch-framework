package com.etake.salesplan.model;

import java.time.LocalDate;
import java.util.Map;

public record DailySales(
        String regionId,
        String storeId,
        String store,
        String similarStore,
        String categoryId,
        String category,
        Map<LocalDate, Sales> sales
) {
}
