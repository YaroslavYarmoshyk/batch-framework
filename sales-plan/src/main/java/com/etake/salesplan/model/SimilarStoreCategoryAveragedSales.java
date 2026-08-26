package com.etake.salesplan.model;

import com.etake.salesplan.model.enumeration.Period;

import java.util.Map;

public record SimilarStoreCategoryAveragedSales(
        StoreCategoryKey key,
        StoreCategoryKey similarKey,
        Map<Period, Sales> similarSales,
        boolean isLfl
) {

    public static SimilarStoreCategoryAveragedSales same(StoreCategoryAveragedSales sales) {
        return new SimilarStoreCategoryAveragedSales(
                sales.key(),
                sales.key(),
                sales.averagedSales(),
                true
        );
    }
}
