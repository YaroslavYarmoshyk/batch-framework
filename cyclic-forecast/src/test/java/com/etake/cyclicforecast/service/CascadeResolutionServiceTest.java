package com.etake.cyclicforecast.service;

import com.etake.cyclicforecast.config.properties.CyclicForecastProperties;
import com.etake.cyclicforecast.config.properties.GoogleSheets;
import com.etake.cyclicforecast.config.properties.HierarchyMapping;
import com.etake.cyclicforecast.config.properties.LookbackWindow;
import com.etake.cyclicforecast.config.properties.Output;
import com.etake.cyclicforecast.config.properties.PlanningScope;
import com.etake.cyclicforecast.config.properties.StoreSimilarity;
import com.etake.cyclicforecast.model.AlgorithmLevel;
import com.etake.cyclicforecast.model.BaselineStats;
import com.etake.cyclicforecast.model.DonorCandidate;
import com.etake.cyclicforecast.model.ForecastResult;
import com.etake.cyclicforecast.model.PlanningScopeRow;
import com.etake.cyclicforecast.model.ProductGroup;
import com.etake.cyclicforecast.repository.DonorCandidateRepository;
import com.etake.cyclicforecast.repository.ProductHierarchyRepository;
import com.etake.cyclicforecast.repository.StoreSimilarityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CascadeResolutionServiceTest {
    private static final String STORE = "store-1";
    private static final String PRODUCT = "product-1";
    private static final String PROMOTION_TYPE = "cyclic";
    private static final LocalDate START_DATE = LocalDate.of(2026, 6, 1);

    @Mock
    private DonorCandidateRepository donorCandidateRepository;
    @Mock
    private StoreSimilarityRepository storeSimilarityRepository;
    @Mock
    private ProductHierarchyRepository productHierarchyRepository;

    private CascadeResolutionService service;
    private PlanningScopeRow row;

    @BeforeEach
    void setUp() {
        final CyclicForecastProperties properties = new CyclicForecastProperties(
                new PlanningScope(START_DATE, START_DATE, PROMOTION_TYPE),
                new LookbackWindow(12),
                new StoreSimilarity(true, true, 12),
                new HierarchyMapping("third_subcategory_id", "second_subcategory_id"),
                14,
                new Output("output/"),
                new GoogleSheets(false, null, null, "result"));
        service = new CascadeResolutionService(
                donorCandidateRepository, storeSimilarityRepository, productHierarchyRepository,
                new AvgDailySalesCalculationService(), properties);
        row = new PlanningScopeRow("row-1", "promo-1", STORE, PRODUCT, PROMOTION_TYPE, "1", START_DATE, 5L);
    }

    @Test
    void level1DonorShortCircuitsWithoutTouchingLowerLevels() {
        final DonorCandidate candidate = new DonorCandidate("donor-promo", STORE, PRODUCT, START_DATE.minusMonths(1), 140, 14);
        when(donorCandidateRepository.findProductDonor(eq(STORE), eq(PRODUCT), eq(PROMOTION_TYPE), any(), eq(START_DATE), eq(row.id())))
                .thenReturn(Optional.of(candidate));

        final ForecastResult result = service.resolve(row);

        assertThat(result.level()).isEqualTo(AlgorithmLevel.LEVEL_1);
        assertThat(result.avgDailySales()).isEqualByComparingTo("10.00");
        assertThat(result.donorKey().toString()).isEqualTo("donor-promo/store-1/product-1/" + START_DATE.minusMonths(1));
        verify(storeSimilarityRepository, never()).findMostSimilarStoreForProduct(any(), any(), any(), any(), any(), any(), any());
        verify(productHierarchyRepository, never()).findGroup(any());
    }

    @Test
    void fallsBackThroughAllLevelsToNoneWhenNothingQualifies() {
        lenient().when(donorCandidateRepository.findProductDonor(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(storeSimilarityRepository.computeBaselineStatsForProduct(any(), any(), any(), any()))
                .thenReturn(new BaselineStats(0, 0));
        lenient().when(storeSimilarityRepository.computeBaselineStatsForGroup(any(), any(), any(), any()))
                .thenReturn(new BaselineStats(0, 0));
        when(productHierarchyRepository.findGroup(PRODUCT))
                .thenReturn(Optional.of(new ProductGroup(PRODUCT, "Product 1", "sub-3", "Sub 3", "sub-2", "Manager")));
        lenient().when(donorCandidateRepository.findThirdSubcategoryDonor(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(donorCandidateRepository.findSecondSubcategoryDonor(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .thenReturn(Optional.empty());

        final ForecastResult result = service.resolve(row);

        assertThat(result.level()).isEqualTo(AlgorithmLevel.NONE);
        assertThat(result.avgDailySales()).isNull();
        assertThat(result.donorKey()).isNull();
        assertThat(result.diagnostics()).isNotEmpty();
        // level 2/4 never attempt a similar-store search once their baseline average daily sales is unavailable (zero stock days)
        verify(storeSimilarityRepository, never()).findMostSimilarStoreForProduct(any(), any(), any(), any(), any(), any(), any());
        verify(storeSimilarityRepository, never()).findMostSimilarStoreForGroup(any(), any(), any(), any(), any(), any(), any());
    }
}
