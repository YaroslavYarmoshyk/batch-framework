package com.etake.storeplanadjustment.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReportColumn {
    DATE("date", 0),
    STORE("store", 1),
    CATEGORY("category", 2),
    TURNOVER("turnover", 3),
    MARGIN("margin", 4),
    ADJUSTED_TURNOVER("adjusted_turnover", 5);

    private final String header;
    private final int index;
}
