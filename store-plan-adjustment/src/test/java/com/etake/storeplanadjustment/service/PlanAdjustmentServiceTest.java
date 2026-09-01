package com.etake.storeplanadjustment.service;

import com.etake.storeplanadjustment.model.AdjustmentRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanAdjustmentServiceTest {

    @Test
    void partialRangeRemovesShareAndAddsActual() {
        // 1000 - 0.25*1000 + 50 = 800
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("0.25"), new BigDecimal("50"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("800")));
    }

    @Test
    void allDayLeavesOnlyActualSales() {
        // share == 1 => plan fully removed, only actual sales remain
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("200"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("200")));
    }

    @Test
    void shareAboveOneIsClampedToOne() {
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("1.5"), new BigDecimal("100"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("100")));
    }

    @Test
    void negativeShareIsClampedToZero() {
        // clamped share=0 => 1000 + 30 = 1030, capped to the plan (1000) since no amount was declared
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("-0.2"), new BigDecimal("30"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("1000")));
    }

    @Test
    void nullPlanTurnoverIsTreatedAsZero() {
        // null plan treated as 0; 0 + 30 = 30, capped to the (zero) plan since no amount was declared
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                null, new BigDecimal("0.5"), new BigDecimal("30"), null);
        assertEquals(0, adjusted.compareTo(BigDecimal.ZERO));
    }

    @Test
    void amountOnlySkipsAlgorithmAndSubtractsDirectly() {
        // no share, no actual sales in range - just plan - amount: 1000 - 300 = 700
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("300"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("700")));
    }

    @Test
    void amountCombinesWithAlgorithmOnRemainingSlots() {
        // 1000 - 0.25*1000 + 50 - 300 = 500
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("0.25"), new BigDecimal("50"), new BigDecimal("300"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("500")));
    }

    @Test
    void algorithmOnlyResultExceedingPlanIsCappedToPlan() {
        // 1000 - 0.5*1000 + 800 = 1300, capped to the plan (1000)
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("0.5"), new BigDecimal("800"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("1000")));
    }

    @Test
    void algorithmOnlyResultBelowPlanIsNotCapped() {
        // 1000 - 0.1*1000 + 10 = 910, below the plan so the cap has no effect
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), new BigDecimal("0.1"), new BigDecimal("10"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("910")));
    }

    @Test
    void resultExceedingPlanIsCappedEvenWhenAmountIsDeclared() {
        // 1000 - 0 + 1500 - 300 = 2200, still capped to the plan (1000) even with a declared amount
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1500"), new BigDecimal("300"));
        assertEquals(0, adjusted.compareTo(new BigDecimal("1000")));
    }

    @Test
    void zeroShareWithActualSalesExceedingPlanIsCapped() {
        // zero share => nothing removed; 1000 + 1500 = 2500, capped to the plan (1000)
        final BigDecimal adjusted = PlanAdjustmentService.adjustedTurnover(
                new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1500"), null);
        assertEquals(0, adjusted.compareTo(new BigDecimal("1000")));
    }

    @Test
    void allocateAmountSplitsProportionallyByTurnover() {
        // category turnover 300 of a 1000 total store/date turnover -> 30% of the 500 amount
        final BigDecimal allocated = PlanAdjustmentService.allocateAmount(
                new BigDecimal("500"), new BigDecimal("300"), new BigDecimal("1000"), 3);
        assertEquals(0, allocated.compareTo(new BigDecimal("150")));
    }

    @Test
    void allocateAmountFallsBackToEqualSplitWhenTotalTurnoverIsZero() {
        final BigDecimal allocated = PlanAdjustmentService.allocateAmount(
                new BigDecimal("300"), BigDecimal.ZERO, BigDecimal.ZERO, 2);
        assertEquals(0, allocated.compareTo(new BigDecimal("150")));
    }

    @Test
    void allocateAmountReturnsZeroWhenAmountIsNull() {
        final BigDecimal allocated = PlanAdjustmentService.allocateAmount(
                null, new BigDecimal("300"), new BigDecimal("1000"), 2);
        assertEquals(0, allocated.compareTo(BigDecimal.ZERO));
    }

    @Test
    void withinRangeKeepsOnlyRowsInsideTheDateRange() {
        final LocalDate fromDate = LocalDate.of(2026, 6, 1);
        final LocalDate toDate = LocalDate.of(2026, 6, 30);
        final AdjustmentRequest before = adjustment("Store A", LocalDate.of(2026, 5, 31));
        final AdjustmentRequest inRange = adjustment("Store B", LocalDate.of(2026, 6, 15));
        final AdjustmentRequest after = adjustment("Store C", LocalDate.of(2026, 7, 1));

        final List<AdjustmentRequest> result =
                PlanAdjustmentService.withinRange(List.of(before, inRange, after), fromDate, toDate);

        assertEquals(List.of(inRange), result);
    }

    @Test
    void withinRangeIncludesBoundaryDates() {
        final LocalDate fromDate = LocalDate.of(2026, 6, 1);
        final LocalDate toDate = LocalDate.of(2026, 6, 30);
        final AdjustmentRequest onFromDate = adjustment("Store A", fromDate);
        final AdjustmentRequest onToDate = adjustment("Store B", toDate);

        final List<AdjustmentRequest> result =
                PlanAdjustmentService.withinRange(List.of(onFromDate, onToDate), fromDate, toDate);

        assertEquals(List.of(onFromDate, onToDate), result);
    }

    @Test
    void withinRangeReturnsEmptyWhenNothingIsInRange() {
        final LocalDate fromDate = LocalDate.of(2026, 6, 1);
        final LocalDate toDate = LocalDate.of(2026, 6, 30);
        final AdjustmentRequest before = adjustment("Store A", LocalDate.of(2026, 5, 1));

        final List<AdjustmentRequest> result =
                PlanAdjustmentService.withinRange(List.of(before), fromDate, toDate);

        assertTrue(result.isEmpty());
    }

    private static AdjustmentRequest adjustment(final String storeName, final LocalDate date) {
        return new AdjustmentRequest(storeName, date, Set.of(), null);
    }
}
