package com.etake.storeplanadjustment.config.properties;

/**
 * File locations for the run. Absolute paths are used as-is; relative paths are resolved by
 * {@link com.etake.storeplanadjustment.utils.ResourcePaths} against the working directory or, when
 * not found there, the module directory. {@code input} is the {@code store_plan_adjustments.xlsx}
 * to read; {@code output} is the report to write (its parent directory is created if missing).
 */
public record Resources(
        String input,
        String output
) {
}
