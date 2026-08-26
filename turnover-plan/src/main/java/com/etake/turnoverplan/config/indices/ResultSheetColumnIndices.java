package com.etake.turnoverplan.config.indices;

import java.util.Set;

public interface ResultSheetColumnIndices {
    Set<Integer> getTurnoverColumnIndices();
    Set<Integer> getMarginColumnIndices();
    int getCategoryOrStoreColumnIndex();
    int getPlanTurnoverFirstMonthColumnIndex();
    int getPlanMarginFirstMonthColumnIndex();
    int getPlanTurnoverSecondMonthColumnIndex();
    int getPlanMarginSecondMonthColumnIndex();
    int getPlanTurnoverThirdMonthColumnIndex();
    int getPlanMarginThirdMonthColumnIndex();
}
