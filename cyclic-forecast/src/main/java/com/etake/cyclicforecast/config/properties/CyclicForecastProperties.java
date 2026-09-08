package com.etake.cyclicforecast.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cyclic-forecast")
public record CyclicForecastProperties(
        PlanningScope planningScope,
        @DefaultValue LookbackWindow lookback,
        @DefaultValue StoreSimilarity storeSimilarity,
        @DefaultValue HierarchyMapping hierarchy,
        @DefaultValue("14") int demandDays,
        Output output,
        @DefaultValue GoogleSheets googleSheets
) {
}
