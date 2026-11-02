package com.etake.promotionforecast.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "system-configurations.google-sheets")
public record GoogleSheetsProperties(
        @DefaultValue("false") boolean enabled,
        String serviceAccountKeyPath,
        String spreadsheetId,
        @DefaultValue("result") String sheetName,
        @DefaultValue Layout layout
) {
    public boolean isConfigured() {
        return enabled && StringUtils.hasText(serviceAccountKeyPath) && StringUtils.hasText(spreadsheetId);
    }

    /**
     * Rebuild of «Прогноз», «Загальна акція» and the manager sheets from the forecast.
     *
     * @param enabled whether the job rebuilds the layout after uploading the source sheet
     * @param force   rebuild even when managers have already entered data for the same promotion
     */
    public record Layout(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("false") boolean force
    ) {
    }

}

