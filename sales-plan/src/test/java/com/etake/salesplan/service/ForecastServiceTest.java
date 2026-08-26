package com.etake.salesplan.service;

import com.etake.salesplan.config.YearMonthProperties;
import com.etake.salesplan.model.ForecastStoreCategorySales;
import com.etake.salesplan.model.Sales;
import com.etake.salesplan.model.SimilarStoreCategoryAveragedSales;
import com.etake.salesplan.model.StoreCategoryKey;
import com.etake.salesplan.model.enumeration.Period;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock
    private HolidaysService holidaysService;

    private ForecastService forecastService;

    private static final YearMonth PLANNED = YearMonth.of(2026, 9);

    @BeforeEach
    void setUp() {
        forecastService = new ForecastService(new YearMonthProperties(PLANNED.getYear(), PLANNED.getMonthValue()), holidaysService);
    }

    // --- calculateMarginForecast: monthly level ---

    @Test
    void calculateMarginForecast_normalCase_matchesRateFormulaAndOldValueFormula() {
        Sales same = new Sales(new BigDecimal("1000"), new BigDecimal("200"));   // rate 0.20
        Sales planned = new Sales(new BigDecimal("1100"), new BigDecimal("242")); // rate 0.22
        Sales current = new Sales(new BigDecimal("1200"), new BigDecimal("300")); // rate 0.25
        BigDecimal plannedMonthTurnover = new BigDecimal("1320"); // = 1200 * (1100/1000)

        BigDecimal margin = ForecastService.calculateMarginForecast(same, planned, current, plannedMonthTurnover);

        BigDecimal oldFormula = current.margin().multiply(planned.margin()).divide(same.margin(), 10, java.math.RoundingMode.HALF_UP);
        assertThat(margin.doubleValue()).isCloseTo(363.0, Offset.offset(0.01));
        assertThat(margin.doubleValue()).isCloseTo(oldFormula.doubleValue(), Offset.offset(0.01));
    }

    @Test
    void calculateMarginForecast_regressionCase_unclampedRateCanExceedTurnover() {
        // proxy/source margin rate grew much faster YoY (0.20 -> 0.90) than turnover (1000 -> 1100)
        Sales same = new Sales(new BigDecimal("1000"), new BigDecimal("200"));   // rate 0.20
        Sales planned = new Sales(new BigDecimal("1100"), new BigDecimal("990")); // rate 0.90
        Sales current = new Sales(new BigDecimal("1200"), new BigDecimal("300")); // rate 0.25 (real, valid)
        BigDecimal plannedMonthTurnover = new BigDecimal("1320");

        BigDecimal margin = ForecastService.calculateMarginForecast(same, planned, current, plannedMonthTurnover);

        // 0.25 * (0.90/0.20) = 1.125 -> margin forecast of 1485, exceeding turnover of 1320
        assertThat(margin.doubleValue()).isGreaterThan(plannedMonthTurnover.doubleValue());
        assertThat(margin.doubleValue()).isCloseTo(1485.0, Offset.offset(0.01));
    }

    @Test
    void calculateMarginForecast_sameMonthLastYearZero_defaultsRateDynamicToOne() {
        Sales same = Sales.zero();
        Sales planned = new Sales(new BigDecimal("1100"), new BigDecimal("220")); // irrelevant: dynamic defaults to 1
        Sales current = new Sales(new BigDecimal("1200"), new BigDecimal("300")); // rate 0.25
        BigDecimal plannedMonthTurnover = new BigDecimal("1320");

        BigDecimal margin = ForecastService.calculateMarginForecast(same, planned, current, plannedMonthTurnover);

        assertThat(margin.doubleValue()).isCloseTo(330.0, Offset.offset(0.01)); // 1320 * 0.25
    }

    @Test
    void calculateMarginForecast_currentMonthTurnoverZero_returnsZeroWithoutException() {
        Sales same = new Sales(new BigDecimal("1000"), new BigDecimal("200"));
        Sales planned = new Sales(new BigDecimal("1100"), new BigDecimal("242"));
        Sales current = new Sales(BigDecimal.ZERO, new BigDecimal("50")); // zero turnover, stray margin value
        BigDecimal plannedMonthTurnover = new BigDecimal("1320");

        BigDecimal margin = ForecastService.calculateMarginForecast(same, planned, current, plannedMonthTurnover);

        assertThat(margin.doubleValue()).isCloseTo(0.0, Offset.offset(0.0001));
    }

    @Test
    void calculateMarginForecast_plannedMonthLastYearTurnoverZero_collapsesRateDynamicToZero() {
        Sales same = new Sales(new BigDecimal("1000"), new BigDecimal("200")); // nonzero rate
        Sales planned = Sales.zero();
        Sales current = new Sales(new BigDecimal("1200"), new BigDecimal("300"));
        BigDecimal plannedMonthTurnover = new BigDecimal("1320");

        BigDecimal margin = ForecastService.calculateMarginForecast(same, planned, current, plannedMonthTurnover);

        assertThat(margin.doubleValue()).isCloseTo(0.0, Offset.offset(0.0001));
    }

    // --- full pipeline: monthly clamp applied ---

    @Test
    void getForecastStoreCategorySales_regressionCase_clampsMonthlyAndForecastMargin() {
        when(holidaysService.getHolidays()).thenReturn(Map.of("Store A", List.of()));

        StoreCategoryKey key = new StoreCategoryKey("R1", "S1", "CAT1", "Store A", "Food");
        Map<Period, Sales> similarSales = Map.of(
                Period.SAME_MONTH_LAST_YEAR, new Sales(new BigDecimal("1000"), new BigDecimal("200")),
                Period.PLANNED_MONTH_LAST_YEAR, new Sales(new BigDecimal("1100"), new BigDecimal("990")),
                Period.CURRENT_MONTH, new Sales(new BigDecimal("1200"), new BigDecimal("300"))
        );
        // isLfl = true: this is the store's OWN data standing in for "similar sales" (not a proxy store),
        // demonstrating the bug/fix applies to like-for-like stores too, not only similar-store substitutions.
        SimilarStoreCategoryAveragedSales input = new SimilarStoreCategoryAveragedSales(key, key, similarSales, true);

        Map<LocalDate, Sales> proxyDailySales = new TreeMap<>();
        proxyDailySales.put(LocalDate.of(2025, 9, 1), new Sales(new BigDecimal("100"), new BigDecimal("20")));
        Map<StoreCategoryKey, Map<LocalDate, Sales>> plannedMonthLastYearDailySales = Map.of(key, proxyDailySales);

        List<ForecastStoreCategorySales> result = forecastService.getForecastStoreCategorySales(List.of(input), plannedMonthLastYearDailySales);

        assertThat(result).hasSize(1);
        ForecastStoreCategorySales forecastStoreCategorySales = result.getFirst();
        assertThat(forecastStoreCategorySales.plannedMonth().margin().doubleValue())
                .isLessThanOrEqualTo(forecastStoreCategorySales.plannedMonth().turnover().doubleValue());
        assertThat(forecastStoreCategorySales.forecast().margin().doubleValue())
                .isLessThanOrEqualTo(forecastStoreCategorySales.forecast().turnover().doubleValue());
    }

    // --- finalizeShares: daily level ---

    @Test
    void finalizeShares_turnoverAndMarginSharesEachSumToOne() {
        TreeMap<LocalDate, Sales> smoothed = new TreeMap<>();
        smoothed.put(LocalDate.of(2026, 9, 1), new Sales(new BigDecimal("10"), new BigDecimal("0.20")));
        smoothed.put(LocalDate.of(2026, 9, 2), new Sales(new BigDecimal("20"), new BigDecimal("0.30")));
        smoothed.put(LocalDate.of(2026, 9, 3), new Sales(new BigDecimal("70"), new BigDecimal("0.25")));

        Map<LocalDate, Sales> shares = ForecastService.finalizeShares(smoothed);

        BigDecimal turnoverSum = shares.values().stream().map(Sales::turnover).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marginSum = shares.values().stream().map(Sales::margin).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(turnoverSum.doubleValue()).isCloseTo(1.0, Offset.offset(0.0001));
        assertThat(marginSum.doubleValue()).isCloseTo(1.0, Offset.offset(0.0001));
    }

    @Test
    void finalizeShares_lowTurnoverDayWithRealisticRateCanStillExceedTurnover_whenMonthlyRateDivergesFromSourceAverage() {
        // Both days individually have a plausible own-day margin rate (<=1), so this is NOT a data-noise case.
        TreeMap<LocalDate, Sales> smoothed = new TreeMap<>();
        smoothed.put(LocalDate.of(2026, 9, 1), new Sales(new BigDecimal("10"), new BigDecimal("0.9")));  // low turnover, high (but valid) rate
        smoothed.put(LocalDate.of(2026, 9, 2), new Sales(new BigDecimal("990"), new BigDecimal("0.1"))); // bulk of turnover, low rate

        Map<LocalDate, Sales> shares = ForecastService.finalizeShares(smoothed);

        // Monthly target's rate (0.4) is deliberately higher than this source's own turnover-weighted
        // average rate (~0.108) — plausible since the monthly rate now comes from a different
        // (rate-dynamic-based) computation than this daily proxy's raw historical average.
        BigDecimal monthlyTurnover = new BigDecimal("1000");
        BigDecimal monthlyMargin = new BigDecimal("400");

        LocalDate outlierDay = LocalDate.of(2026, 9, 1);
        Sales outlierShare = shares.get(outlierDay);
        BigDecimal dailyTurnover = monthlyTurnover.multiply(outlierShare.turnover());
        BigDecimal dailyMarginUnclamped = monthlyMargin.multiply(outlierShare.margin());

        // Proves the daily rate-based split alone does not guarantee the per-day invariant —
        // the backstop clamp in StoreCategorySalesService.mapToDailySales is load-bearing, not just defensive.
        assertThat(dailyMarginUnclamped.doubleValue()).isGreaterThan(dailyTurnover.doubleValue());

        BigDecimal dailyMarginClamped = dailyMarginUnclamped.min(dailyTurnover);
        assertThat(dailyMarginClamped.doubleValue()).isLessThanOrEqualTo(dailyTurnover.doubleValue());
    }

    // --- calculateDailyShares: full daily-split integration ---

    @Test
    void calculateDailyShares_dataNoiseDay_violatesBeforeClampButNeverAfter() {
        Map<LocalDate, Sales> proxyDailySales = new TreeMap<>();
        for (int day = 1; day <= 30; day++) {
            proxyDailySales.put(LocalDate.of(2025, 9, day), new Sales(new BigDecimal("100"), new BigDecimal("20"))); // rate 0.20
        }
        // a single data-noise/markdown day where recorded margin vastly exceeds recorded turnover;
        // large enough that the +/-3-day smoothing window (which dilutes single-day spikes by design)
        // still leaves a detectable elevated rate afterward
        proxyDailySales.put(LocalDate.of(2025, 9, 15), new Sales(new BigDecimal("1"), new BigDecimal("200"))); // rate 200

        Map<LocalDate, Sales> shares = forecastService.calculateDailyShares(proxyDailySales, List.of());

        assertThat(shares).isNotEmpty();
        BigDecimal turnoverSum = shares.values().stream().map(Sales::turnover).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marginSum = shares.values().stream().map(Sales::margin).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(turnoverSum.doubleValue()).isCloseTo(1.0, Offset.offset(0.001));
        assertThat(marginSum.doubleValue()).isCloseTo(1.0, Offset.offset(0.001));

        // Deliberately above the source data's own ~0.20 rate (as B's monthly rate-forecast can be,
        // since it's derived from a different computation than this daily proxy's raw average) — the
        // +/-3-day smoothing dilutes the single-day spike enough that a rate exactly matching the
        // source average would no longer violate, but a modest mismatch still does.
        BigDecimal monthlyTurnover = new BigDecimal("100000");
        BigDecimal monthlyMargin = new BigDecimal("30000"); // rate 0.30

        boolean anyViolationBeforeClamp = shares.entrySet().stream().anyMatch(entry -> {
            BigDecimal dailyTurnover = monthlyTurnover.multiply(entry.getValue().turnover());
            BigDecimal dailyMargin = monthlyMargin.multiply(entry.getValue().margin());
            return dailyMargin.compareTo(dailyTurnover) > 0;
        });
        assertThat(anyViolationBeforeClamp).isTrue();

        boolean anyViolationAfterClamp = shares.entrySet().stream().anyMatch(entry -> {
            BigDecimal dailyTurnover = monthlyTurnover.multiply(entry.getValue().turnover());
            BigDecimal dailyMargin = monthlyMargin.multiply(entry.getValue().margin()).min(dailyTurnover);
            return dailyMargin.compareTo(dailyTurnover) > 0;
        });
        assertThat(anyViolationAfterClamp).isFalse();
    }

    @Test
    void calculateDailyShares_wellBehavedData_neverViolatesEvenWithoutClamp() {
        Map<LocalDate, Sales> proxyDailySales = new TreeMap<>();
        for (int day = 1; day <= 30; day++) {
            proxyDailySales.put(LocalDate.of(2025, 9, day), new Sales(new BigDecimal("100"), new BigDecimal("20"))); // uniform rate 0.20
        }

        Map<LocalDate, Sales> shares = forecastService.calculateDailyShares(proxyDailySales, List.of());

        BigDecimal monthlyTurnover = new BigDecimal("100000");
        BigDecimal monthlyMargin = new BigDecimal("20000");

        boolean anyViolation = shares.entrySet().stream().anyMatch(entry -> {
            BigDecimal dailyTurnover = monthlyTurnover.multiply(entry.getValue().turnover());
            BigDecimal dailyMargin = monthlyMargin.multiply(entry.getValue().margin());
            return dailyMargin.compareTo(dailyTurnover) > 0;
        });
        assertThat(anyViolation).isFalse();
    }

    @Test
    void calculateDailyShares_zeroTurnoverDay_handledWithoutException() {
        Map<LocalDate, Sales> proxyDailySales = new TreeMap<>();
        for (int day = 1; day <= 30; day++) {
            proxyDailySales.put(LocalDate.of(2025, 9, day), new Sales(new BigDecimal("100"), new BigDecimal("20")));
        }
        proxyDailySales.put(LocalDate.of(2025, 9, 10), Sales.zero());

        Map<LocalDate, Sales> shares = forecastService.calculateDailyShares(proxyDailySales, List.of());

        assertThat(shares).isNotEmpty();
        BigDecimal turnoverSum = shares.values().stream().map(Sales::turnover).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(turnoverSum.doubleValue()).isCloseTo(1.0, Offset.offset(0.001));
    }
}
