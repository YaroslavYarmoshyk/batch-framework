package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.config.SystemConfigurationProperties;
import com.etake.avgcheckplan.config.properties.Adjustment;
import com.etake.avgcheckplan.config.properties.DateRange;
import com.etake.avgcheckplan.model.AvgCheckPosition;
import com.etake.avgcheckplan.model.ForecastPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

import static com.etake.avgcheckplan.utils.Constants.PRECISE_MATH_CONTEXT;
import static java.time.temporal.TemporalAdjusters.lastDayOfMonth;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class PlanedMonthAvgCheckForecastService implements AvgCheckForecastService<ForecastPosition> {
    private final CheckPositionService checkPositionService;
    private final SystemConfigurationProperties systemConfigurationProperties;
    private final SimilarStoreResolver similarStoreResolver;

    public List<ForecastPosition> getForecastPositions() {
        final DateRange dateRange = systemConfigurationProperties.dateRange();
        final LocalDate fromDate = dateRange.fromDate();
        final LocalDate toDate = dateRange.toDate();

        final Function<LocalDate, LocalDate> lastDay = d -> d.with(lastDayOfMonth());

        final LocalDate prevMonthFromCurrentYear = fromDate.minusMonths(1);
        final LocalDate prevMonthLastDayCurrentYear = lastDay.apply(prevMonthFromCurrentYear);

        final LocalDate prevYearFromCurrentMonth = fromDate.minusYears(1);
        final LocalDate prevYearToCurrentMonth = toDate.minusYears(1);
        final LocalDate prevYearLastDayCurrentMonth = lastDay.apply(prevYearFromCurrentMonth);

        final LocalDate prevYearFromPrevMonth = prevYearFromCurrentMonth.minusMonths(1);
        final LocalDate prevYearLastDayPrevMonth = lastDay.apply(prevYearFromPrevMonth);

        final Map<String, AvgCheckPosition> prevYearPrevMonthLastDayPositions = fetchAvgCheckPositionsMap(prevYearFromPrevMonth, prevYearLastDayPrevMonth);
        final Map<String, AvgCheckPosition> prevYearCurrentMonthToDatePositions = fetchAvgCheckPositionsMap(prevYearFromCurrentMonth, prevYearToCurrentMonth);
        final Map<String, AvgCheckPosition> prevYearCurrentMonthLastDayPositions = fetchAvgCheckPositionsMap(prevYearFromCurrentMonth, prevYearLastDayCurrentMonth);

        final Map<String, AvgCheckPosition> currentYearPrevMonthLastDayPositions = fetchAvgCheckPositionsMap(prevMonthFromCurrentYear, prevMonthLastDayCurrentYear);
        final Map<String, AvgCheckPosition> currentYearCurrentMonthToDatePositions = fetchAvgCheckPositionsMap(fromDate, toDate);
        final Map<String, AvgCheckPosition> currentYearCurrentMonthLastDayPositions = checkPositionService.getAvgCheckPositionsInCurrentPeriod()
                .stream().collect(toMap(AvgCheckPosition::store, identity()));

        return currentYearCurrentMonthToDatePositions.entrySet().stream()
                .map(entry -> buildForecastPosition(entry.getKey(),
                        entry.getValue(),
                        currentYearPrevMonthLastDayPositions,
                        prevYearCurrentMonthToDatePositions,
                        prevYearCurrentMonthLastDayPositions,
                        prevYearPrevMonthLastDayPositions,
                        currentYearCurrentMonthToDatePositions,
                        currentYearCurrentMonthLastDayPositions))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ForecastPosition::region))
                .toList();
    }

    private Map<String, AvgCheckPosition> fetchAvgCheckPositionsMap(final LocalDate from, final LocalDate to) {
        return checkPositionService.getAvgCheckPositionsInPeriod(from, to)
                .stream()
                .collect(toMap(AvgCheckPosition::store, identity()));
    }

    private ForecastPosition buildForecastPosition(
            final String currentStore,
            final AvgCheckPosition currentYearCurrentMonthToDatePosition,
            final Map<String, AvgCheckPosition> currentYearPrevMonthLastDayPositions,
            final Map<String, AvgCheckPosition> prevYearCurrentMonthToDatePositions,
            final Map<String, AvgCheckPosition> prevYearCurrentMonthLastDayPositions,
            final Map<String, AvgCheckPosition> prevYearPrevMonthLastDayPositions,
            final Map<String, AvgCheckPosition> currentYearCurrentMonthToDatePositions,
            final Map<String, AvgCheckPosition> currentYearCurrentMonthLastDayPositions) {

        final AvgCheckPosition currentYearPrevMonthLastDayPosition = currentYearPrevMonthLastDayPositions.get(currentStore);
        if (currentYearPrevMonthLastDayPosition == null) return null;

        String similarStore = currentStore;
        if (!prevYearCurrentMonthToDatePositions.containsKey(currentStore)) {
            final String region = currentYearCurrentMonthToDatePosition.region();
            final Map<String, BigDecimal> prevYearDynamics = prevYearPrevMonthLastDayPositions.entrySet().stream()
                    .filter(e -> e.getValue().region().equals(region))
                    .filter(e -> prevYearCurrentMonthToDatePositions.containsKey(e.getKey()))
                    .collect(toMap(
                            Map.Entry::getKey,
                            e -> {
                                final BigDecimal prevMonthAvgCheck = e.getValue().avgCheck();
                                final BigDecimal currentMonthAvgCheck = prevYearCurrentMonthToDatePositions.get(e.getKey()).avgCheck();
                                return currentMonthAvgCheck.divide(prevMonthAvgCheck, PRECISE_MATH_CONTEXT);
                            }));

            final BigDecimal currentYearDynamic = currentYearPrevMonthLastDayPosition.avgCheck()
                    .divide(currentYearCurrentMonthToDatePositions.get(currentStore).avgCheck(), PRECISE_MATH_CONTEXT);

            similarStore = similarStoreResolver.findClosestStore(currentYearDynamic, prevYearDynamics)
                    .orElseThrow();
        }

        final AvgCheckPosition similarPrevYearCurrentMonthToDatePosition = prevYearCurrentMonthToDatePositions.get(similarStore);
        final AvgCheckPosition similarPrevYearCurrentMonthLastDayPosition = prevYearCurrentMonthLastDayPositions.get(similarStore);

        final BigDecimal dynamic = similarPrevYearCurrentMonthLastDayPosition.avgCheck()
                .divide(similarPrevYearCurrentMonthToDatePosition.avgCheck(), PRECISE_MATH_CONTEXT);

        final BigDecimal avgCheckForecast = currentYearCurrentMonthToDatePositions.get(currentStore).avgCheck().multiply(dynamic);
        final AvgCheckPosition currentYearCurrentMonthLastDayPosition = currentYearCurrentMonthLastDayPositions.get(currentStore);
        final BigDecimal currentAvgCheck = currentYearCurrentMonthLastDayPosition.avgCheck();
        final BigDecimal smoothedAvgCheckForecast = getAdjustedForecast(currentAvgCheck, avgCheckForecast);

        final BigDecimal prevYearCurrentMonthToDateDynamic = Optional.ofNullable(prevYearCurrentMonthToDatePositions.get(currentStore))
                .map(AvgCheckPosition::avgCheck).orElse(null);
        final BigDecimal prevYearCurrentMonthLastDayDynamic = Optional.ofNullable(prevYearCurrentMonthLastDayPositions.get(currentStore))
                .map(AvgCheckPosition::avgCheck).orElse(null);

        final BigDecimal currYearAvgCheckToDate = currentYearCurrentMonthToDatePosition.avgCheck();
        final BigDecimal currYearAvgCheckLastDay = currentYearCurrentMonthLastDayPosition.avgCheck();
        final BigDecimal fulfilment = currYearAvgCheckLastDay.divide(avgCheckForecast, PRECISE_MATH_CONTEXT);
        final BigDecimal adjustedFulfilment = currYearAvgCheckLastDay.divide(smoothedAvgCheckForecast, PRECISE_MATH_CONTEXT);

        return new ForecastPosition(
                currentYearCurrentMonthToDatePosition.region(),
                currentYearCurrentMonthToDatePosition.store(),
                similarStore,
                prevYearCurrentMonthToDateDynamic,
                prevYearCurrentMonthLastDayDynamic,
                dynamic,
                currYearAvgCheckToDate,
                avgCheckForecast,
                currYearAvgCheckLastDay,
                fulfilment,
                smoothedAvgCheckForecast,
                adjustedFulfilment
        );
    }

    private BigDecimal getAdjustedForecast(final BigDecimal currentAvgCheck, final BigDecimal avgCheckForecast) {
        final Adjustment adjustmentProperties = systemConfigurationProperties.adjustment();
        final BigDecimal forecastPercentage = currentAvgCheck
                .divide(BigDecimal.ONE.subtract(adjustmentProperties.growth()), PRECISE_MATH_CONTEXT)
                .divide(avgCheckForecast, PRECISE_MATH_CONTEXT);


        final BigDecimal smoothingFactor = forecastPercentage.subtract(adjustmentProperties.smoothingFactor());
        final BigDecimal adjustmentStrength = smoothingFactor.abs().multiply(adjustmentProperties.strengthFactor());

        final BigDecimal adjustment = avgCheckForecast.multiply(adjustmentStrength);

        BigDecimal smoothedAvgCheckForecast;

        if (forecastPercentage.compareTo(adjustmentProperties.minLimit()) < 0) {
            // forecast too high → lower it a bit
            smoothedAvgCheckForecast = avgCheckForecast.subtract(adjustment);
        } else if (forecastPercentage.compareTo(adjustmentProperties.maxLimit()) > 0) {
            // forecast too low → raise it a bit
            smoothedAvgCheckForecast = avgCheckForecast.add(adjustment);
        } else {
            // forecast within normal range — no smoothing
            smoothedAvgCheckForecast = avgCheckForecast;
        }
        return smoothedAvgCheckForecast;
    }
}
