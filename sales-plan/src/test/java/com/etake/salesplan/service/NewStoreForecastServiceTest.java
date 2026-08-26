package com.etake.salesplan.service;

import com.etake.salesplan.config.NewStoreProperties;
import com.etake.salesplan.model.CategoryCatalogEntry;
import com.etake.salesplan.model.ForecastStoreCategorySales;
import com.etake.salesplan.model.NewStoreCandidate;
import com.etake.salesplan.model.Sales;
import com.etake.salesplan.repository.StoreCategorySalesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewStoreForecastServiceTest {

    @Mock
    private StoreCategorySalesRepository storeCategorySalesRepository;
    @Mock
    private PlanCatalogService planCatalogService;
    @Mock
    private HolidaysService holidaysService;

    private NewStoreForecastService newStoreForecastService;

    private static final YearMonth PLANNED = YearMonth.of(2026, 9);

    @BeforeEach
    void setUp() {
        newStoreForecastService = new NewStoreForecastService(
                storeCategorySalesRepository,
                planCatalogService,
                holidaysService,
                new NewStoreProperties(new BigDecimal("0.95"))
        );

        when(planCatalogService.getCategoryTurnoverWeights()).thenReturn(Map.of(
                "Food", new BigDecimal("0.60"),
                "Non-Food", new BigDecimal("0.40")
        ));
        when(planCatalogService.getCategoryMarginRates()).thenReturn(Map.of(
                "Food", new BigDecimal("0.20"),
                "Non-Food", new BigDecimal("0.30")
        ));
        when(storeCategorySalesRepository.getCategoryCatalog()).thenReturn(List.of(
                new CategoryCatalogEntry("CAT-FOOD", "Food"),
                new CategoryCatalogEntry("CAT-NONFOOD", "Non-Food")
        ));
        when(holidaysService.getHolidays()).thenReturn(Map.of());
    }

    @Test
    void tradingDataBased_achievesConfiguredTarget() {
        NewStoreCandidate candidate = new NewStoreCandidate(
                "R1", "S1", "New Store", LocalDate.of(2026, 7, 15),
                new BigDecimal("10000"), new BigDecimal("2000"), 10
        );
        when(planCatalogService.getStoreTargets()).thenReturn(Map.of());

        List<ForecastStoreCategorySales> result = newStoreForecastService.getNewStoreForecastSales(List.of(candidate), PLANNED);

        assertThat(result).hasSize(2);
        BigDecimal totalPlanTurnover = result.stream()
                .map(f -> f.forecast().turnover())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dailyAvg = candidate.turnover().divide(BigDecimal.valueOf(candidate.tradingDays()), 10, java.math.RoundingMode.HALF_UP);
        BigDecimal expectedActual = dailyAvg.multiply(BigDecimal.valueOf(PLANNED.lengthOfMonth()));
        BigDecimal achievement = expectedActual.divide(totalPlanTurnover, 4, java.math.RoundingMode.HALF_UP);

        assertThat(achievement.doubleValue()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void categorySharesSumToStoreTotal() {
        NewStoreCandidate candidate = new NewStoreCandidate(
                "R1", "S1", "New Store", LocalDate.of(2026, 7, 15),
                new BigDecimal("10000"), new BigDecimal("2000"), 10
        );
        when(planCatalogService.getStoreTargets()).thenReturn(Map.of());

        List<ForecastStoreCategorySales> result = newStoreForecastService.getNewStoreForecastSales(List.of(candidate), PLANNED);

        BigDecimal sumTurnover = result.stream().map(f -> f.forecast().turnover()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = candidate.turnover()
                .divide(BigDecimal.valueOf(candidate.tradingDays()), 10, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(PLANNED.lengthOfMonth()))
                .divide(new BigDecimal("0.95"), 10, java.math.RoundingMode.HALF_UP);

        assertThat(sumTurnover.doubleValue()).isCloseTo(expectedTotal.doubleValue(), org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void dailySharesSumToOne() {
        NewStoreCandidate candidate = new NewStoreCandidate(
                "R1", "S1", "New Store", LocalDate.of(2026, 7, 15),
                new BigDecimal("10000"), new BigDecimal("2000"), 10
        );
        when(planCatalogService.getStoreTargets()).thenReturn(Map.of());
        when(holidaysService.getHolidays()).thenReturn(Map.of("New Store", List.of(LocalDate.of(2026, 9, 1))));

        List<ForecastStoreCategorySales> result = newStoreForecastService.getNewStoreForecastSales(List.of(candidate), PLANNED);

        Map<LocalDate, Sales> dailyShares = result.getFirst().dailyShares();
        assertThat(dailyShares).doesNotContainKey(LocalDate.of(2026, 9, 1));

        BigDecimal sumShare = dailyShares.values().stream().map(Sales::turnover).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumShare.doubleValue()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void zeroTradingDays_withStoresPlanEntry_usesManualTargetDirectly() {
        NewStoreCandidate candidate = new NewStoreCandidate(
                "R1", "S2", "Brand New Store", LocalDate.of(2026, 8, 28),
                BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        when(planCatalogService.getStoreTargets()).thenReturn(Map.of("Brand New Store", new BigDecimal("50000")));

        List<ForecastStoreCategorySales> result = newStoreForecastService.getNewStoreForecastSales(List.of(candidate), PLANNED);

        BigDecimal sumTurnover = result.stream().map(f -> f.forecast().turnover()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumTurnover.doubleValue()).isCloseTo(50000.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void zeroTradingDays_withoutStoresPlanEntry_isSkipped() {
        NewStoreCandidate candidate = new NewStoreCandidate(
                "R1", "S3", "Unplanned Store", LocalDate.of(2026, 8, 30),
                BigDecimal.ZERO, BigDecimal.ZERO, 0
        );
        when(planCatalogService.getStoreTargets()).thenReturn(Map.of());

        List<ForecastStoreCategorySales> result = newStoreForecastService.getNewStoreForecastSales(List.of(candidate), PLANNED);

        assertThat(result).isEmpty();
    }
}
