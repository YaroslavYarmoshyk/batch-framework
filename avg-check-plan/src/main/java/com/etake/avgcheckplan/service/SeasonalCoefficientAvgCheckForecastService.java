package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.config.SystemConfigurationProperties;
import com.etake.avgcheckplan.config.properties.SeasonalCoefficient;
import com.etake.avgcheckplan.model.AvgCheckPosition;
import com.etake.avgcheckplan.model.SeasonalPlanPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.etake.avgcheckplan.utils.Constants.PRECISE_MATH_CONTEXT;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class SeasonalCoefficientAvgCheckForecastService implements AvgCheckForecastService<SeasonalPlanPosition> {
    private final CheckPositionService checkPositionService;
    private final SystemConfigurationProperties systemConfigurationProperties;
    private final SimilarStoreResolver similarStoreResolver;

    @Override
    public List<SeasonalPlanPosition> getForecastPositions() {
        final LocalDate targetMonthStart = systemConfigurationProperties.dateRange().fromDate().withDayOfMonth(1);
        final LocalDate baseMonthStart = targetMonthStart.minusMonths(1);

        final Map<String, AvgCheckPosition> baseCurrentYearPositions = fetchFullMonthPositions(baseMonthStart);
        final Map<String, AvgCheckPosition> targetLastYearPositions = fetchFullMonthPositions(targetMonthStart.minusYears(1));
        final Map<String, AvgCheckPosition> baseLastYearPositions = fetchFullMonthPositions(baseMonthStart.minusYears(1));
        final Map<String, AvgCheckPosition> targetYearBeforeLastPositions = fetchFullMonthPositions(targetMonthStart.minusYears(2));
        final Map<String, AvgCheckPosition> baseYearBeforeLastPositions = fetchFullMonthPositions(baseMonthStart.minusYears(2));

        return baseCurrentYearPositions.values().stream()
                .map(basePosition -> buildPlanPosition(basePosition,
                        baseCurrentYearPositions,
                        targetLastYearPositions,
                        baseLastYearPositions,
                        targetYearBeforeLastPositions,
                        baseYearBeforeLastPositions))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SeasonalPlanPosition::region))
                .toList();
    }

    private Map<String, AvgCheckPosition> fetchFullMonthPositions(final LocalDate monthStart) {
        return checkPositionService.getAvgCheckPositionsInPeriod(monthStart, monthStart.with(lastDayOfMonth()))
                .stream()
                .collect(toMap(AvgCheckPosition::store, identity()));
    }

    private SeasonalPlanPosition buildPlanPosition(
            final AvgCheckPosition basePosition,
            final Map<String, AvgCheckPosition> baseCurrentYearPositions,
            final Map<String, AvgCheckPosition> targetLastYearPositions,
            final Map<String, AvgCheckPosition> baseLastYearPositions,
            final Map<String, AvgCheckPosition> targetYearBeforeLastPositions,
            final Map<String, AvgCheckPosition> baseYearBeforeLastPositions) {

        final String store = basePosition.store();
        final String region = basePosition.region();
        final BigDecimal baseAvgCheck = basePosition.avgCheck();
        if (baseAvgCheck == null) return null;

        String similarStore = null;
        String coefficientStore = store;
        BigDecimal k1 = seasonalCoefficient(store, targetLastYearPositions, baseLastYearPositions);
        if (k1 == null) {
            similarStore = findSimilarStore(store, region, baseAvgCheck,
                    baseCurrentYearPositions, targetLastYearPositions, baseLastYearPositions);
            if (similarStore == null) return null;
            coefficientStore = similarStore;
            k1 = seasonalCoefficient(coefficientStore, targetLastYearPositions, baseLastYearPositions);
            if (k1 == null) return null;
        }

        final BigDecimal k2 = seasonalCoefficient(coefficientStore, targetYearBeforeLastPositions, baseYearBeforeLastPositions);
        final boolean singleYearCoefficient = k2 == null;

        boolean stableSeasonality = true;
        final BigDecimal appliedCoefficient;
        if (singleYearCoefficient) {
            appliedCoefficient = k1;
        } else if (k1.subtract(k2).abs().compareTo(seasonalCoefficientProperties().divergenceThreshold()) <= 0) {
            final BigDecimal recentYearWeight = seasonalCoefficientProperties().recentYearWeight();
            appliedCoefficient = k1.multiply(recentYearWeight, PRECISE_MATH_CONTEXT)
                    .add(k2.multiply(BigDecimal.ONE.subtract(recentYearWeight), PRECISE_MATH_CONTEXT));
        } else {
            appliedCoefficient = k1;
            stableSeasonality = false;
        }

        final BigDecimal plannedAvgCheck = baseAvgCheck.multiply(appliedCoefficient, PRECISE_MATH_CONTEXT)
                .divide(BigDecimal.ONE.subtract(seasonalCoefficientProperties().growth()), PRECISE_MATH_CONTEXT);

        return new SeasonalPlanPosition(
                region,
                store,
                similarStore,
                baseAvgCheck,
                k1,
                k2,
                appliedCoefficient,
                plannedAvgCheck,
                stableSeasonality,
                singleYearCoefficient
        );
    }

    private String findSimilarStore(
            final String store,
            final String region,
            final BigDecimal baseAvgCheck,
            final Map<String, AvgCheckPosition> baseCurrentYearPositions,
            final Map<String, AvgCheckPosition> targetLastYearPositions,
            final Map<String, AvgCheckPosition> baseLastYearPositions) {

        // a new store has no history of its own, so candidates are compared by the closest
        // base month avg check within the same region, among stores with a computable K1
        final Map<String, BigDecimal> candidateBaseAvgChecks = baseCurrentYearPositions.values().stream()
                .filter(position -> !position.store().equals(store))
                .filter(position -> position.region().equals(region))
                .filter(position -> position.avgCheck() != null)
                .filter(position -> seasonalCoefficient(position.store(), targetLastYearPositions, baseLastYearPositions) != null)
                .collect(toMap(AvgCheckPosition::store, AvgCheckPosition::avgCheck));

        return similarStoreResolver.findClosestStore(baseAvgCheck, candidateBaseAvgChecks).orElse(null);
    }

    private static BigDecimal seasonalCoefficient(
            final String store,
            final Map<String, AvgCheckPosition> targetMonthPositions,
            final Map<String, AvgCheckPosition> baseMonthPositions) {

        final AvgCheckPosition targetPosition = targetMonthPositions.get(store);
        final AvgCheckPosition basePosition = baseMonthPositions.get(store);
        if (targetPosition == null || basePosition == null) return null;

        final BigDecimal targetAvgCheck = targetPosition.avgCheck();
        final BigDecimal baseAvgCheck = basePosition.avgCheck();
        if (targetAvgCheck == null || baseAvgCheck == null || baseAvgCheck.signum() == 0) return null;

        return targetAvgCheck.divide(baseAvgCheck, PRECISE_MATH_CONTEXT);
    }

    private SeasonalCoefficient seasonalCoefficientProperties() {
        return systemConfigurationProperties.seasonalCoefficient();
    }
}
