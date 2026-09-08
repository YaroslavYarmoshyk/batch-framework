package com.etake.cyclicforecast.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record LookbackWindow(
        @DefaultValue("12") int months
) {
}
