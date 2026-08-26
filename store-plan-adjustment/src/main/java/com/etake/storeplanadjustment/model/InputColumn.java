package com.etake.storeplanadjustment.model;

/**
 * Columns of the {@code store_plan_adjustments.xlsx} input file. Their indices are configured
 * under {@code system-configuration.input-columns} so the layout can change without code edits.
 */
public enum InputColumn {
    STORE,
    DATE,
    TIME_RANGES
}
