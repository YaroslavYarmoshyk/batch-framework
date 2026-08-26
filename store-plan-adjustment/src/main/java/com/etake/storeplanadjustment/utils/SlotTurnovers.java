package com.etake.storeplanadjustment.utils;

import com.etake.storeplanadjustment.model.SlotTurnover;
import com.etake.storeplanadjustment.model.StoreCategoryDate;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SlotTurnovers {

    private SlotTurnovers() {
    }

    /**
     * Groups raw slot rows into {@code store/category/date -> (slot start -> turnover)}. A given
     * (store, category, date, slot) is unique in the query result, so no merging of slots is needed.
     */
    public static Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> groupByStoreCategoryDate(
            final List<SlotTurnover> rows) {
        final Map<StoreCategoryDate, Map<LocalTime, BigDecimal>> grouped = new HashMap<>();
        for (final SlotTurnover row : rows) {
            final StoreCategoryDate key = new StoreCategoryDate(row.storeId(), row.categoryId(), row.date());
            grouped.computeIfAbsent(key, k -> new HashMap<>())
                    .merge(row.slot(), row.turnover(), BigDecimal::add);
        }
        return grouped;
    }
}
