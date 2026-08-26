package com.etake.salesplan.model;

public record StoreCategoryKey(
        String regionId,
        String storeId,
        String categoryId,
        String storeName,
        String categoryName
) {
}
