package com.etake.avgcheckplan.model;

import java.math.BigDecimal;

/**
 * One row of the {@code store_checks_plans} table.
 */
public record StoreCheckPlanRecord(
        String storeId,
        long year,
        long month,
        BigDecimal avgCheck,
        BigDecimal avgItemsPerCheck
) {
}
