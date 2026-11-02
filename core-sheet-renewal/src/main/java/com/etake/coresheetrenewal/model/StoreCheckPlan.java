package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;

public record StoreCheckPlan(String storeName, BigDecimal avgCheck, BigDecimal avgItemsPerCheck) {
}
