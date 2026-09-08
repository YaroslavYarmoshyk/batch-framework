package com.etake.cyclicforecast.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

public record Output(
        @DefaultValue("output/") String excelOutputDirectory
) {
}
