package com.etake.storeplanadjustment.model;

/**
 * An input file row that could not be read: its row number as shown in Excel, its cells as the user
 * sees them, and the reason in Ukrainian.
 */
public record InvalidAdjustmentRow(
        int rowNumber,
        String store,
        String date,
        String timeRanges,
        String amount,
        String reason
) {
}
