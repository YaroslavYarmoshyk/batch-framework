package com.etake.salesplan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "system-configurations.new-store")
public record NewStoreProperties(BigDecimal achievementTarget) {
}
