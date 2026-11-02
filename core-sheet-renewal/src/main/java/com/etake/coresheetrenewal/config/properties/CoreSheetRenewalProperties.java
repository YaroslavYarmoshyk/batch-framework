package com.etake.coresheetrenewal.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.YearMonth;

/**
 * @param year            year of the month whose sheet is created
 * @param month           month whose sheet is created; the previous month's sheet receives the actuals
 * @param abcStockEnabled whether the ABC stock columns of the previous month are filled
 */
@ConfigurationProperties(prefix = "system-configurations")
public record CoreSheetRenewalProperties(
        int year,
        int month,
        boolean abcStockEnabled,
        GoogleSheets googleSheets
) {
    public YearMonth newMonth() {
        return YearMonth.of(year, month);
    }

    public record GoogleSheets(String serviceAccountKeyPath, String spreadsheetId) {
    }
}
