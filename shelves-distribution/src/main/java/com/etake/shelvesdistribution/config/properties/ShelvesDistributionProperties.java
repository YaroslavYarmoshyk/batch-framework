package com.etake.shelvesdistribution.config.properties;

import com.etake.shelvesdistribution.model.enumeration.Granularity;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "features.distribution")
public record ShelvesDistributionProperties(
        boolean groupedByStore,
        Granularity granularity,
        String region,
        List<String> stores) {
}
