package com.etake.avgcheckplan;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Column {
    REGION("Регіон"),
    STORE("Магазин"),
    SIMILAR_STORE("Схожий магазин"),
    PREV_AVG_CHECK_TO_DATE("СЧ попереднього року до дати"),
    PREV_AVG_CHECK_LAST_DAY("СЧ попереднього року увесь місяць"),
    DYNAMIC("Динаміка"),
    AVG_CHECK_TO_DATE("СЧ до дати"),
    FORECAST("Прогноз"),
    CURRENT_AVG_CHECK("Теперішній СЧ"),
    FULFILMENT("Виконання"),
    ADJUSTED_FORECAST("Скорегований прогноз СЧ"),
    ADJUSTED_FULFILMENT("Скореговане виконання");

    private final String name;
}
