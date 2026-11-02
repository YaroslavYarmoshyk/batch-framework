package com.etake.promotionforecast.config;

import com.etake.googlesheets.GoogleSheetsGateway;
import com.etake.googlesheets.ResourcePaths;
import com.etake.promotionforecast.config.properties.GoogleSheetsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleSheetsConfiguration {

    @Bean
    public GoogleSheetsGateway googleSheetsGateway(final GoogleSheetsProperties properties) {
        return new GoogleSheetsGateway("promotion-forecast",
                ResourcePaths.resolve(properties.serviceAccountKeyPath()),
                properties.spreadsheetId());
    }
}
