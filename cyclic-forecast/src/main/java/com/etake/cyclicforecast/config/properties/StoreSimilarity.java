package com.etake.cyclicforecast.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record StoreSimilarity(
        @DefaultValue("true") boolean requireSameStoreFormat,
        @DefaultValue("true") boolean allowFormatFallback,
        @DefaultValue("12") int baselineLookbackMonths
) {
}
