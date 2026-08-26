package com.etake.salesplan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "system-configurations.year-month")
public record YearMonthProperties(int year, int month) {
}
