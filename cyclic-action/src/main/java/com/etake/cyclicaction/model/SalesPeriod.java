package com.etake.cyclicaction.model;

import java.math.BigDecimal;

public record SalesPeriod(
        String store,
        Integer actionCode,
        BigDecimal actionAverageSales
) {}