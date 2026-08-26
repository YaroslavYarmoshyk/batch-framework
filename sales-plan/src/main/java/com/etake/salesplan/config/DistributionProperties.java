package com.etake.salesplan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("system-configurations.distribution")
public record DistributionProperties(boolean enabled, int maxIterations, BigDecimal epsilon) {
}
