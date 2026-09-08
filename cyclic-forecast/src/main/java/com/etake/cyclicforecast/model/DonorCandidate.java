package com.etake.cyclicforecast.model;

import java.time.LocalDate;

public record DonorCandidate(
        String promotionId,
        String storeId,
        String productId,
        LocalDate startDate,
        long totalUnits,
        long daysOnStock
) {
}
