package com.etake.coresheetrenewal.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StoreDailySales(String storeName, LocalDate date, BigDecimal sales, BigDecimal margin) {
}
