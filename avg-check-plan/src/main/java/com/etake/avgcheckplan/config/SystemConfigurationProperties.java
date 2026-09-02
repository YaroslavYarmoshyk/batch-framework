package com.etake.avgcheckplan.config;

import com.etake.avgcheckplan.Column;
import com.etake.avgcheckplan.config.properties.Adjustment;
import com.etake.avgcheckplan.config.properties.DateRange;
import com.etake.avgcheckplan.config.properties.ForecastStrategy;
import com.etake.avgcheckplan.config.properties.SeasonalCoefficient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Map;

@ConfigurationProperties(prefix = "system-configuration")
public record SystemConfigurationProperties(
        DateRange dateRange,
        Adjustment adjustment,
        @DefaultValue
        SeasonalCoefficient seasonalCoefficient,
        @DefaultValue("PLANED_MONTH")
        ForecastStrategy forecastStrategy,
        String output,
        String input,
        Map<Column, Integer> columns
) {
}
