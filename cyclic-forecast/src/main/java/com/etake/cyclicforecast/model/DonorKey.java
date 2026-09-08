package com.etake.cyclicforecast.model;

import java.time.LocalDate;

public record DonorKey(
        String promotionId,
        String storeId,
        String productId,
        LocalDate startDate
) {
    @Override
    public String toString() {
        return promotionId + "/" + storeId + "/" + productId + "/" + startDate;
    }
}
