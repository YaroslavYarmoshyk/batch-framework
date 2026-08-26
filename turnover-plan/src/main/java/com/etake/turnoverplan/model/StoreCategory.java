package com.etake.turnoverplan.model;

public record StoreCategory(String regionName, String storeName, String categoryName) {

    public String getKey() {
        return String.format("%s_%s", storeName, categoryName);
    }
}
