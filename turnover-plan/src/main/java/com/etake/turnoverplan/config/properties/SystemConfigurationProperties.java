package com.etake.turnoverplan.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.List;


@ConfigurationProperties(prefix = "system-configuration")
public record SystemConfigurationProperties(
        Integer plannedYear,
        Integer plannedMonth,
        String output,
        List<LocalDate> exclusionDates
) {
}
