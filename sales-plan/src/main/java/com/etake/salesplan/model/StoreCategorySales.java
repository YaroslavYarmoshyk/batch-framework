package com.etake.salesplan.model;

import com.etake.salesplan.model.enumeration.Period;

import java.time.LocalDate;
import java.util.Map;

public record StoreCategorySales(
        StoreCategoryKey key,
        Map<Period, Map<LocalDate, Sales>> sales
) {
}
