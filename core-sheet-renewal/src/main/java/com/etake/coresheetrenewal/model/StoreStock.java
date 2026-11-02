package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;

/** Stock value at purchase prices of one ABC category of a store. */
public record StoreStock(String storeName, AbcCategory abcCategory, BigDecimal amount) {
}
