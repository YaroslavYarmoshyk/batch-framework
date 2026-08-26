package com.etake.salesplan.utils.stream;

import com.etake.salesplan.model.Sales;
import com.etake.salesplan.model.StoreCategoryKey;
import com.etake.salesplan.model.StoreCategorySalesRecord;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SalesCollectors {

    public static Collector<StoreCategorySalesRecord,
            ?, Map<StoreCategoryKey, Map<LocalDate, Sales>>> byKeyThenDay(Integer year, Integer month) {

        return Collectors.groupingBy(
                SalesCollectors::keyOf,
                Collectors.toMap(
                        record -> LocalDate.of(year, month, record.dayOfMonth()),
                        r -> new Sales(r.turnover(), r.margin()),
                        (_, b) -> b,        // if duplicate day appears — keep last
                        TreeMap::new        // sorted by day
                )
        );
    }

    private static StoreCategoryKey keyOf(StoreCategorySalesRecord r) {
        return new StoreCategoryKey(
                r.regionId(),
                r.storeId(),
                r.categoryId(),
                r.storeName(),
                r.categoryName()
        );
    }
}
