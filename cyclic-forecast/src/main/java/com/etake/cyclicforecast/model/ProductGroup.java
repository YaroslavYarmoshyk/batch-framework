package com.etake.cyclicforecast.model;

public record ProductGroup(
        String productId,
        String productName,
        String thirdSubcategoryId,
        String thirdSubcategoryName,
        String secondSubcategoryId,
        String manager
) {
}
