package com.etake.turnoverplan.config.indices;

import java.util.Set;

public record CategoriesSheetColumnIndices(
        int category,
        int planTurnoverFirstMonth,
        int planMarginFirstMonth,
        int planTurnoverSecondMonth,
        int planMarginSecondMonth,
        int planTurnoverThirdMonth,
        int planMarginThirdMonth
) implements ResultSheetColumnIndices {
    @Override
    public Set<Integer> getTurnoverColumnIndices() {
        return Set.of(planTurnoverFirstMonth, planTurnoverSecondMonth, planTurnoverThirdMonth);
    }

    @Override
    public Set<Integer> getMarginColumnIndices() {
        return Set.of(planMarginFirstMonth, planMarginSecondMonth, planMarginThirdMonth);
    }

    @Override
    public int getCategoryOrStoreColumnIndex() {
        return category;
    }

    @Override
    public int getPlanTurnoverFirstMonthColumnIndex() {
        return planTurnoverFirstMonth;
    }

    @Override
    public int getPlanMarginFirstMonthColumnIndex() {
        return planMarginFirstMonth;
    }

    @Override
    public int getPlanTurnoverSecondMonthColumnIndex() {
        return planTurnoverSecondMonth;
    }

    @Override
    public int getPlanMarginSecondMonthColumnIndex() {
        return planMarginSecondMonth;
    }

    @Override
    public int getPlanTurnoverThirdMonthColumnIndex() {
        return planTurnoverThirdMonth;
    }

    @Override
    public int getPlanMarginThirdMonthColumnIndex() {
        return planMarginThirdMonth;
    }
}
