package com.etake.cyclicforecast.service;

import com.etake.cyclicforecast.config.properties.CyclicForecastProperties;
import com.etake.cyclicforecast.model.AlgorithmLevel;
import com.etake.cyclicforecast.model.BaselineStats;
import com.etake.cyclicforecast.model.DonorCandidate;
import com.etake.cyclicforecast.model.DonorKey;
import com.etake.cyclicforecast.model.ForecastResult;
import com.etake.cyclicforecast.model.PlanningScopeRow;
import com.etake.cyclicforecast.model.ProductGroup;
import com.etake.cyclicforecast.repository.DonorCandidateRepository;
import com.etake.cyclicforecast.repository.ProductHierarchyRepository;
import com.etake.cyclicforecast.repository.StoreSimilarityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class CascadeResolutionService {
    private final DonorCandidateRepository donorCandidateRepository;
    private final StoreSimilarityRepository storeSimilarityRepository;
    private final ProductHierarchyRepository productHierarchyRepository;
    private final AvgDailySalesCalculationService avgDailySalesCalculationService;
    private final CyclicForecastProperties properties;

    public ForecastResult resolve(final PlanningScopeRow row) {
        final LocalDate lookbackFrom = row.startDate().minusMonths(properties.lookback().months());
        final List<String> diagnostics = new ArrayList<>();

        Optional<ForecastResult> result = tryLevel1(row, lookbackFrom);
        if (result.isEmpty()) {
            diagnostics.add("level 1: no donor found in store " + row.storeId());
            result = tryLevel2(row, lookbackFrom, diagnostics);
        }
        if (result.isPresent()) {
            return result.get();
        }

        final Optional<ProductGroup> group = productHierarchyRepository.findGroup(row.productId());
        if (group.isEmpty()) {
            diagnostics.add("no category hierarchy mapping found for product " + row.productId());
            return ForecastResult.noData(row, diagnostics);
        }

        result = tryLevel3(row, group.get().thirdSubcategoryId(), lookbackFrom);
        if (result.isEmpty()) {
            diagnostics.add("level 3: no donor found for subcategory " + group.get().thirdSubcategoryId() + " in store " + row.storeId());
            result = tryLevel4(row, group.get().thirdSubcategoryId(), lookbackFrom, diagnostics);
        }
        if (result.isPresent()) {
            return result.get();
        }

        result = tryLevel5(row, group.get().secondSubcategoryId(), lookbackFrom);
        if (result.isEmpty()) {
            diagnostics.add("level 5: no donor found for group " + group.get().secondSubcategoryId() + " in store " + row.storeId());
            return ForecastResult.noData(row, diagnostics);
        }
        return result.get();
    }

    private Optional<ForecastResult> tryLevel1(final PlanningScopeRow row, final LocalDate lookbackFrom) {
        return donorCandidateRepository.findProductDonor(row.storeId(), row.productId(), row.promotionType(), lookbackFrom, row.startDate(), row.id())
                .map(candidate -> toResult(row, AlgorithmLevel.LEVEL_1, candidate));
    }

    private Optional<ForecastResult> tryLevel2(final PlanningScopeRow row, final LocalDate lookbackFrom, final List<String> diagnostics) {
        final LocalDate baselineLookbackFrom = row.startDate().minusMonths(properties.storeSimilarity().baselineLookbackMonths());
        final BaselineStats baseline = storeSimilarityRepository.computeBaselineStatsForProduct(row.storeId(), row.productId(), baselineLookbackFrom, row.startDate());
        final Optional<BigDecimal> baselineAvgDailySales = avgDailySalesCalculationService.computeIfPossible(baseline.totalUnits(), baseline.daysOnStock());
        if (baselineAvgDailySales.isEmpty()) {
            diagnostics.add("level 2: no non-promotional baseline sales for store " + row.storeId() + " / product " + row.productId());
            return Optional.empty();
        }

        final Optional<String> similarStore = findSimilarStore(row.storeFormat(),
                format -> storeSimilarityRepository.findMostSimilarStoreForProduct(
                        row.storeId(), format, row.productId(), row.promotionType(), lookbackFrom, row.startDate(), baselineAvgDailySales.get()));
        if (similarStore.isEmpty()) {
            diagnostics.add("level 2: no similar store found for product " + row.productId());
            return Optional.empty();
        }

        return donorCandidateRepository.findProductDonor(similarStore.get(), row.productId(), row.promotionType(), lookbackFrom, row.startDate(), row.id())
                .map(candidate -> toResult(row, AlgorithmLevel.LEVEL_2, candidate));
    }

    private Optional<ForecastResult> tryLevel3(final PlanningScopeRow row, final String thirdSubcategoryId, final LocalDate lookbackFrom) {
        return donorCandidateRepository.findThirdSubcategoryDonor(row.storeId(), thirdSubcategoryId, row.promotionType(), lookbackFrom, row.startDate(), row.id())
                .map(candidate -> toResult(row, AlgorithmLevel.LEVEL_3, candidate));
    }

    private Optional<ForecastResult> tryLevel4(final PlanningScopeRow row, final String thirdSubcategoryId, final LocalDate lookbackFrom, final List<String> diagnostics) {
        final LocalDate baselineLookbackFrom = row.startDate().minusMonths(properties.storeSimilarity().baselineLookbackMonths());
        final BaselineStats baseline = storeSimilarityRepository.computeBaselineStatsForGroup(row.storeId(), thirdSubcategoryId, baselineLookbackFrom, row.startDate());
        final Optional<BigDecimal> baselineAvgDailySales = avgDailySalesCalculationService.computeIfPossible(baseline.totalUnits(), baseline.daysOnStock());
        if (baselineAvgDailySales.isEmpty()) {
            diagnostics.add("level 4: no non-promotional baseline sales for store " + row.storeId() + " / subcategory " + thirdSubcategoryId);
            return Optional.empty();
        }

        final Optional<String> similarStore = findSimilarStore(row.storeFormat(),
                format -> storeSimilarityRepository.findMostSimilarStoreForGroup(
                        row.storeId(), format, thirdSubcategoryId, row.promotionType(), lookbackFrom, row.startDate(), baselineAvgDailySales.get()));
        if (similarStore.isEmpty()) {
            diagnostics.add("level 4: no similar store found for subcategory " + thirdSubcategoryId);
            return Optional.empty();
        }

        return donorCandidateRepository.findThirdSubcategoryDonor(similarStore.get(), thirdSubcategoryId, row.promotionType(), lookbackFrom, row.startDate(), row.id())
                .map(candidate -> toResult(row, AlgorithmLevel.LEVEL_4, candidate));
    }

    private Optional<ForecastResult> tryLevel5(final PlanningScopeRow row, final String secondSubcategoryId, final LocalDate lookbackFrom) {
        return donorCandidateRepository.findSecondSubcategoryDonor(row.storeId(), secondSubcategoryId, row.promotionType(), lookbackFrom, row.startDate(), row.id())
                .map(candidate -> toResult(row, AlgorithmLevel.LEVEL_5, candidate));
    }

    /**
     * Tries the configured store_similarity search restricted to the target's own store_format
     * first; if that finds nothing and {@code store-similarity.allow-format-fallback} is enabled,
     * retries unrestricted.
     */
    private Optional<String> findSimilarStore(final String storeFormat, final Function<String, Optional<String>> finder) {
        final String format = properties.storeSimilarity().requireSameStoreFormat() ? storeFormat : null;
        final Optional<String> similar = finder.apply(format);
        if (similar.isPresent() || format == null || !properties.storeSimilarity().allowFormatFallback()) {
            return similar;
        }
        return finder.apply(null);
    }

    private ForecastResult toResult(final PlanningScopeRow row, final AlgorithmLevel level, final DonorCandidate candidate) {
        final BigDecimal avgDailySales = avgDailySalesCalculationService.compute(candidate.totalUnits(), candidate.daysOnStock());
        final DonorKey donorKey = new DonorKey(candidate.promotionId(), candidate.storeId(), candidate.productId(), candidate.startDate());
        return new ForecastResult(row, level, avgDailySales, donorKey, List.of());
    }
}
