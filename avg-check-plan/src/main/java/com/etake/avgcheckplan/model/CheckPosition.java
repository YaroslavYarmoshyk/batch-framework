package com.etake.avgcheckplan.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CheckPosition(
        UUID id,
        LocalDate date,
        String regionName,
        String storeName,
        boolean refunded,
        BigDecimal amount
) {
}
