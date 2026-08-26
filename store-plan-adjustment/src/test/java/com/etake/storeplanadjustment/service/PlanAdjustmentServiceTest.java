package com.etake.storeplanadjustment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanAdjustmentServiceTest {

    @Test
    void partialRangeRemovesShareAndAddsActual() {
        // 1000 - 0.25*1000 + 50 = 800
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("0.25"), new BigDecimal("50"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("800")));
    }

    @Test
    void allDayLeavesOnlyActualSales() {
        // share == 1 => plan fully removed, only actual sales remain
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("200"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("200")));
    }

    @Test
    void shareAboveOneIsClampedToOne() {
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("1.5"), new BigDecimal("100"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("100")));
    }

    @Test
    void negativeShareIsClampedToZero() {
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("-0.2"), new BigDecimal("30"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("1030")));
    }

    @Test
    void nullPlanTurnoverIsTreatedAsZero() {
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                null, new BigDecimal("0.5"), new BigDecimal("30"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("30")));
    }
}
