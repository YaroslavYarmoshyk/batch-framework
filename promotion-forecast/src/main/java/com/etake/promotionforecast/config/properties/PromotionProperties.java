package com.etake.promotionforecast.config.properties;

import com.etake.promotionforecast.model.enumeration.PromotionType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.Period;

@ConfigurationProperties(prefix = "system-configurations.promotion")
public record PromotionProperties(
        PromotionType type,
        @DateTimeFormat(pattern = "dd.MM.yy")
        LocalDate startDate,
        @DateTimeFormat(pattern = "dd.MM.yy")
        LocalDate endDate,
        Period offset,
        int historicalMinDaysOnStock) {
}
