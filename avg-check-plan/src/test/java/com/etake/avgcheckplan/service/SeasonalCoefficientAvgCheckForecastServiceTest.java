package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.config.SystemConfigurationProperties;
import com.etake.avgcheckplan.config.properties.DateRange;
import com.etake.avgcheckplan.config.properties.ForecastStrategy;
import com.etake.avgcheckplan.config.properties.SeasonalCoefficient;
import com.etake.avgcheckplan.model.AvgCheckPosition;
import com.etake.avgcheckplan.model.SeasonalPlanPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.etake.avgcheckplan.utils.Constants.PRECISE_MATH_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeasonalCoefficientAvgCheckForecastServiceTest {
    private static final String REGION = "Центр";
    private static final String STORE = "Магазин 1";
    private static final BigDecimal RECENT_YEAR_WEIGHT = new BigDecimal("0.6");
    private static final BigDecimal GROWTH = new BigDecimal("0.02");

    // target month = July 2026, base month = June 2026
    private static final LocalDate BASE_CURR_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate BASE_CURR_TO = LocalDate.of(2026, 6, 30);
    private static final LocalDate TARGET_LAST_FROM = LocalDate.of(2025, 7, 1);
    private static final LocalDate TARGET_LAST_TO = LocalDate.of(2025, 7, 31);
    private static final LocalDate BASE_LAST_FROM = LocalDate.of(2025, 6, 1);
    private static final LocalDate BASE_LAST_TO = LocalDate.of(2025, 6, 30);
    private static final LocalDate TARGET_YBL_FROM = LocalDate.of(2024, 7, 1);
    private static final LocalDate TARGET_YBL_TO = LocalDate.of(2024, 7, 31);
    private static final LocalDate BASE_YBL_FROM = LocalDate.of(2024, 6, 1);
    private static final LocalDate BASE_YBL_TO = LocalDate.of(2024, 6, 30);

    @Mock
    private CheckPositionService checkPositionService;

    private SeasonalCoefficientAvgCheckForecastService service;

    @BeforeEach
    void setUp() {
        final SystemConfigurationProperties properties = new SystemConfigurationProperties(
                new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15)),
                null,
                new SeasonalCoefficient(new BigDecimal("0.05"), RECENT_YEAR_WEIGHT, GROWTH),
                ForecastStrategy.SEASONAL_COEFFICIENT,
                null,
                null);
        service = new SeasonalCoefficientAvgCheckForecastService(checkPositionService, properties, new SimilarStoreResolver());
        mockPeriods(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void shouldUseWeightedAverageWhenCoefficientsAreStable() {
        mockPeriods(
                List.of(position(STORE, "100")),
                List.of(position(STORE, "110")),
                List.of(position(STORE, "100")),
                List.of(position(STORE, "108")),
                List.of(position(STORE, "100")));

        final List<SeasonalPlanPosition> positions = service.getForecastPositions();

        assertThat(positions).hasSize(1);
        final SeasonalPlanPosition position = positions.getFirst();
        final BigDecimal expectedK1 = new BigDecimal("110").divide(new BigDecimal("100"), PRECISE_MATH_CONTEXT);
        final BigDecimal expectedK2 = new BigDecimal("108").divide(new BigDecimal("100"), PRECISE_MATH_CONTEXT);
        final BigDecimal expectedCoefficient = expectedK1.multiply(RECENT_YEAR_WEIGHT, PRECISE_MATH_CONTEXT)
                .add(expectedK2.multiply(BigDecimal.ONE.subtract(RECENT_YEAR_WEIGHT), PRECISE_MATH_CONTEXT));
        assertThat(position.region()).isEqualTo(REGION);
        assertThat(position.store()).isEqualTo(STORE);
        assertThat(position.similarStore()).isNull();
        assertThat(position.baseAvgCheck()).isEqualByComparingTo("100");
        assertThat(position.k1()).isEqualByComparingTo(expectedK1);
        assertThat(position.k2()).isEqualByComparingTo(expectedK2);
        assertThat(position.appliedCoefficient()).isEqualByComparingTo(expectedCoefficient);
        // 0.6 * 1.1 + 0.4 * 1.08 = 1.092
        assertThat(position.appliedCoefficient()).isEqualByComparingTo("1.092");
        assertThat(position.plannedAvgCheck()).isEqualByComparingTo(withGrowth(new BigDecimal("100").multiply(expectedCoefficient, PRECISE_MATH_CONTEXT)));
        assertThat(position.stableSeasonality()).isTrue();
        assertThat(position.singleYearCoefficient()).isFalse();
    }

    @Test
    void shouldFallBackToK1WhenCoefficientsDiverge() {
        mockPeriods(
                List.of(position(STORE, "100")),
                List.of(position(STORE, "120")),
                List.of(position(STORE, "100")),
                List.of(position(STORE, "100")),
                List.of(position(STORE, "100")));

        final List<SeasonalPlanPosition> positions = service.getForecastPositions();

        assertThat(positions).hasSize(1);
        final SeasonalPlanPosition position = positions.getFirst();
        assertThat(position.k1()).isEqualByComparingTo("1.2");
        assertThat(position.k2()).isEqualByComparingTo("1");
        assertThat(position.appliedCoefficient()).isEqualByComparingTo("1.2");
        assertThat(position.plannedAvgCheck()).isEqualByComparingTo(withGrowth(new BigDecimal("120")));
        assertThat(position.stableSeasonality()).isFalse();
        assertThat(position.singleYearCoefficient()).isFalse();
    }

    @Test
    void shouldUseSingleYearCoefficientWhenYearBeforeLastIsMissing() {
        mockPeriods(
                List.of(position(STORE, "100")),
                List.of(position(STORE, "110")),
                List.of(position(STORE, "100")),
                List.of(),
                List.of());

        final List<SeasonalPlanPosition> positions = service.getForecastPositions();

        assertThat(positions).hasSize(1);
        final SeasonalPlanPosition position = positions.getFirst();
        assertThat(position.k1()).isEqualByComparingTo("1.1");
        assertThat(position.k2()).isNull();
        assertThat(position.appliedCoefficient()).isEqualByComparingTo("1.1");
        assertThat(position.plannedAvgCheck()).isEqualByComparingTo(withGrowth(new BigDecimal("110")));
        assertThat(position.stableSeasonality()).isTrue();
        assertThat(position.singleYearCoefficient()).isTrue();
    }

    @Test
    void shouldBorrowCoefficientsFromSimilarStoreWhenLastYearIsMissing() {
        final String newStore = "Новий магазин";
        final String similarStore = "Магазин 2";
        final String distantStore = "Магазин 3";
        mockPeriods(
                List.of(position(newStore, "100"), position(similarStore, "105"), position(distantStore, "500")),
                List.of(position(similarStore, "110"), position(distantStore, "600")),
                List.of(position(similarStore, "100"), position(distantStore, "500")),
                List.of(position(similarStore, "108"), position(distantStore, "550")),
                List.of(position(similarStore, "100"), position(distantStore, "500")));

        final List<SeasonalPlanPosition> positions = service.getForecastPositions();

        assertThat(positions).hasSize(3);
        final SeasonalPlanPosition position = positions.stream()
                .filter(p -> p.store().equals(newStore))
                .findFirst()
                .orElseThrow();
        assertThat(position.similarStore()).isEqualTo(similarStore);
        assertThat(position.k1()).isEqualByComparingTo("1.1");
        assertThat(position.k2()).isEqualByComparingTo("1.08");
        assertThat(position.baseAvgCheck()).isEqualByComparingTo("100");
        assertThat(position.singleYearCoefficient()).isFalse();
    }

    @Test
    void shouldSkipStoreWithoutBaseMonthData() {
        mockPeriods(
                List.of(),
                List.of(position(STORE, "110")),
                List.of(position(STORE, "100")),
                List.of(position(STORE, "108")),
                List.of(position(STORE, "100")));

        assertThat(service.getForecastPositions()).isEmpty();
    }

    @Test
    void shouldTreatZeroBaseAvgCheckAsMissingCoefficient() {
        mockPeriods(
                List.of(position(STORE, "100")),
                List.of(position(STORE, "110")),
                List.of(position(STORE, "100")),
                List.of(position(STORE, "108")),
                List.of(position(STORE, "0")));

        final List<SeasonalPlanPosition> positions = service.getForecastPositions();

        assertThat(positions).hasSize(1);
        final SeasonalPlanPosition position = positions.getFirst();
        assertThat(position.k2()).isNull();
        assertThat(position.singleYearCoefficient()).isTrue();
        assertThat(position.appliedCoefficient()).isEqualByComparingTo("1.1");
    }

    private void mockPeriods(
            final List<AvgCheckPosition> baseCurrentYear,
            final List<AvgCheckPosition> targetLastYear,
            final List<AvgCheckPosition> baseLastYear,
            final List<AvgCheckPosition> targetYearBeforeLast,
            final List<AvgCheckPosition> baseYearBeforeLast) {
        lenient().when(checkPositionService.getAvgCheckPositionsInPeriod(BASE_CURR_FROM, BASE_CURR_TO)).thenReturn(baseCurrentYear);
        lenient().when(checkPositionService.getAvgCheckPositionsInPeriod(TARGET_LAST_FROM, TARGET_LAST_TO)).thenReturn(targetLastYear);
        lenient().when(checkPositionService.getAvgCheckPositionsInPeriod(BASE_LAST_FROM, BASE_LAST_TO)).thenReturn(baseLastYear);
        lenient().when(checkPositionService.getAvgCheckPositionsInPeriod(TARGET_YBL_FROM, TARGET_YBL_TO)).thenReturn(targetYearBeforeLast);
        lenient().when(checkPositionService.getAvgCheckPositionsInPeriod(BASE_YBL_FROM, BASE_YBL_TO)).thenReturn(baseYearBeforeLast);
    }

    private static AvgCheckPosition position(final String store, final String avgCheck) {
        return new AvgCheckPosition(REGION, store, new BigDecimal(avgCheck));
    }

    private static BigDecimal withGrowth(final BigDecimal plannedAvgCheck) {
        return plannedAvgCheck.divide(BigDecimal.ONE.subtract(GROWTH), PRECISE_MATH_CONTEXT);
    }
}
