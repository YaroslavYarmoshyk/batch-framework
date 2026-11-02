package com.etake.coresheetrenewal.sheet;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The columns of a month sheet this job fills or resets. */
@Getter
@RequiredArgsConstructor
public enum CoreSheetColumn {
    SALES_PLAN("C"),
    AVG_CHECK_PLAN("K"),
    AVG_CHECK_FACT("L"),
    LOYALTY_AVG_CHECK_FACT("O"),
    ITEMS_PER_CHECK_PLAN("Q"),
    ITEMS_PER_CHECK_FACT("R"),
    STOCK_A("Z"),
    STOCK_D("AC"),
    MARGIN("AF"),
    FIRST_DAY("AI"),
    LAST_DAY("BM"),
    DAYS("BO");

    private final String letter;

    /** 0-based index of the column. */
    public int index() {
        int index = 0;
        for (final char c : letter.toCharArray()) {
            index = index * 26 + (c - 'A' + 1);
        }
        return index - 1;
    }
}
