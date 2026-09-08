package com.etake.cyclicforecast.config.properties;

import java.time.LocalDate;

public record PlanningScope(
        LocalDate fromDate,
        LocalDate toDate,
        String promotionType
) {
}
