package com.etake.salesplan.model;

import java.math.BigDecimal;

public record CategoryPlan(String categoryName, BigDecimal turnover, BigDecimal margin) {
}
