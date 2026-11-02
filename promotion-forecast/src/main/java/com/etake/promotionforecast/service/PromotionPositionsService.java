package com.etake.promotionforecast.service;

import com.etake.promotionforecast.config.properties.PromotionProperties;
import com.etake.promotionforecast.model.ForecastPosition;
import com.etake.promotionforecast.model.PromotionPosition;
import com.etake.promotionforecast.model.enumeration.AlgorithmLevel;
import com.etake.promotionforecast.model.enumeration.PromotionType;
import com.etake.promotionforecast.repository.PromotionPositionsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionPositionsService {
    private record LevelRule(AlgorithmLevel level, BiPredicate<PromotionPosition, PromotionPosition> matches) {
    }

    private record Alignment(PromotionPosition position, AlgorithmLevel level) {
        static final Alignment NONE = new Alignment(null, AlgorithmLevel.NONE);
    }

    private static final List<LevelRule> LEVEL_RULES = List.of(
            new LevelRule(AlgorithmLevel.LEVEL_1, PromotionPositionsService::isFirstLevel),
            new LevelRule(AlgorithmLevel.LEVEL_2, PromotionPositionsService::isSecondLevel),
            new LevelRule(AlgorithmLevel.LEVEL_3, PromotionPositionsService::isThirdLevel),
            new LevelRule(AlgorithmLevel.LEVEL_4, PromotionPositionsService::isFourthLevel),
            new LevelRule(AlgorithmLevel.LEVEL_5, PromotionPositionsService::isFifthLevel),
            new LevelRule(AlgorithmLevel.LEVEL_6, PromotionPositionsService::isSixthLevel)
    );

    private final PromotionPositionsRepository promotionPositionsRepository;
    private final PromotionProperties promotionProperties;

    public List<ForecastPosition> forecast() {
        validatePromotion();

        LocalDate to = promotionProperties.endDate();
        LocalDate from = to.minus(promotionProperties.offset());
        List<PromotionPosition> promotionPositions = promotionPositionsRepository.getPromotionPositions(
                promotionProperties.type(),
                from,
                to
        );

        Map<Boolean, List<PromotionPosition>> groupedPositions = promotionPositions.stream()
                .collect(Collectors.partitioningBy(
                        position ->
                                position.startDate().isEqual(promotionProperties.startDate())
                                        && position.endDate().isEqual(promotionProperties.endDate()))
                );

        List<PromotionPosition> actualPromotionPositions = groupedPositions.get(Boolean.TRUE);
        if (actualPromotionPositions == null) {
            throw new IllegalStateException("Cannot find current promotion");
        }

        List<PromotionPosition> historicalPositions = groupedPositions.get(Boolean.FALSE).stream()
                .filter(position -> position.daysOnStock() >= promotionProperties.historicalMinDaysOnStock())
                .filter(position -> position.soldUnits() > 0)
                .toList();

        long promotionDays = ChronoUnit.DAYS.between(promotionProperties.startDate(), promotionProperties.endDate()) + 1;

        List<ForecastPosition> forecastPositions = actualPromotionPositions.stream()
                .map(position -> {
                            Alignment alignment = findAlignment(position, historicalPositions);
                            return getForecastPosition(alignment.position(), position, alignment.level(), promotionDays);
                        }
                ).toList();

        log.info("Forecasted {} promotion positions using {} historical positions",
                forecastPositions.size(), historicalPositions.size());
        return forecastPositions;
    }

    private void validatePromotion() {
        PromotionType type = promotionProperties.type();
        LocalDate startDate = promotionProperties.startDate();
        LocalDate endDate = promotionProperties.endDate();

        if (type == null || startDate == null || endDate == null) {
            throw new IllegalArgumentException("Promotion type, start date and end date must be provided");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(
                    "Promotion start date %s is after end date %s".formatted(startDate, endDate));
        }
        if (!promotionPositionsRepository.existsPromotion(type, startDate, endDate)) {
            throw new IllegalArgumentException("No '%s' promotion found for period %s - %s"
                    .formatted(type.getName(), startDate, endDate));
        }
    }

    private static Alignment findAlignment(PromotionPosition actualPosition,
                                           List<PromotionPosition> historicalPositions) {
        for (LevelRule rule : LEVEL_RULES) {
            Optional<PromotionPosition> latest = historicalPositions.stream()
                    .filter(historical -> rule.matches().test(historical, actualPosition))
                    .max(Comparator.comparing(PromotionPosition::startDate));
            if (latest.isPresent()) {
                return new Alignment(latest.get(), rule.level());
            }
        }
        return Alignment.NONE;
    }


    private static boolean isFirstLevel(PromotionPosition historical, PromotionPosition actual) {
        return historical.productId().equalsIgnoreCase(actual.productId())
                && historical.storeId().equalsIgnoreCase(actual.storeId());
    }

    private static boolean isSecondLevel(PromotionPosition historical, PromotionPosition actual) {
        return historical.productId().equalsIgnoreCase(actual.productId())
                && historical.storeFormat().equalsIgnoreCase(actual.storeFormat());
    }

    private static boolean isThirdLevel(PromotionPosition historical, PromotionPosition actual) {
        return historical.productId().equalsIgnoreCase(actual.productId());
    }

    private static boolean isFourthLevel(PromotionPosition historical, PromotionPosition actual) {
        return historical.storeId().equalsIgnoreCase(actual.storeId())
                && historical.thirdSubcategoryId().equalsIgnoreCase(actual.thirdSubcategoryId());
    }

    private static boolean isFifthLevel(PromotionPosition historical, PromotionPosition actual) {
        return historical.storeFormat().equalsIgnoreCase(actual.storeFormat())
                && historical.thirdSubcategoryId().equalsIgnoreCase(actual.thirdSubcategoryId());
    }

    private static boolean isSixthLevel(PromotionPosition historical, PromotionPosition actual) {
        return historical.thirdSubcategoryId().equalsIgnoreCase(actual.thirdSubcategoryId());
    }

    private static ForecastPosition getForecastPosition(PromotionPosition historical,
                                                        PromotionPosition actual,
                                                        AlgorithmLevel algorithmLevel,
                                                        long promotionDays) {
        BigDecimal averageDailySales = historical == null ? BigDecimal.ONE : BigDecimal.valueOf(historical.soldUnits())
                .divide(BigDecimal.valueOf(historical.daysOnStock()), MathContext.DECIMAL64);
        BigDecimal forecast = averageDailySales.multiply(BigDecimal.valueOf(promotionDays))
                .add(BigDecimal.valueOf(actual.carryover()));

        return new ForecastPosition(
                actual,
                historical,
                algorithmLevel,
                averageDailySales,
                forecast
        );
    }
}
