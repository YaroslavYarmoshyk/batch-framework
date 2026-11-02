package com.etake.coresheetrenewal.config;

import com.etake.coresheetrenewal.config.properties.CoreSheetRenewalProperties;
import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.googlesheets.ResourcePaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleSheetsConfiguration {

    @Bean
    public GoogleSheetsGateway googleSheetsGateway(final CoreSheetRenewalProperties properties) {
        return new GoogleSheetsGateway("core-sheet-renewal",
                ResourcePaths.resolve(properties.googleSheets().serviceAccountKeyPath()),
                properties.googleSheets().spreadsheetId());
    }
}
