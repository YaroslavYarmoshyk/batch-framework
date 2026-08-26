package com.etake.storeplanadjustment.config.properties;

import com.etake.storeplanadjustment.model.InputColumn;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "system-configuration")
public record SystemConfigurationProperties(
        DateRange dateRange,
        Resources resources,
        Map<InputColumn, Integer> inputColumns
) {
}
