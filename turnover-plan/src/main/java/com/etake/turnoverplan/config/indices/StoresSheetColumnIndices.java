package com.etake.turnoverplan.config.indices;

import java.util.Set;

public record StoresSheetColumnIndices(
        int store,
        int planTurnoverFirstMonth,
        int planMarginFirstMonth,
        int planTurnoverSecondMonth,
        int planMarginSecondMonth,
        int planTurnoverThirdMonth,
        int planMarginThirdMonth,
        int adjustedPlanTurnoverFirstMonth,
        int adjustedPlanMarginFirstMonth
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
        return store;
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
