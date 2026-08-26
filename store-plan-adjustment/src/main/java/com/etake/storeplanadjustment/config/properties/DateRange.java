package com.etake.storeplanadjustment.config.properties;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record DateRange(
        @DateTimeFormat(pattern = "dd.MM.yyyy")
        LocalDate fromDate,
        @DateTimeFormat(pattern = "dd.MM.yyyy")
        LocalDate toDate
) {
}
