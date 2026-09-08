package com.etake.cyclicforecast.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record GoogleSheets(
        @DefaultValue("false") boolean enabled,
        String serviceAccountKeyPath,
        String spreadsheetId,
        @DefaultValue("result") String sheetName
) {
}
