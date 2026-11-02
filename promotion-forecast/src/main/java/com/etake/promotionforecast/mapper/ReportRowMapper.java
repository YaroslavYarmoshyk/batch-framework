package com.etake.promotionforecast.mapper;

import com.etake.promotionforecast.config.properties.PromotionProperties;
import com.etake.promotionforecast.model.ForecastPosition;
import com.etake.promotionforecast.model.PromotionPosition;
import com.etake.promotionforecast.model.ReportRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class ReportRowMapper {
    private final PromotionProperties promotionProperties;

    public List<ReportRow> map(final List<ForecastPosition> positions) {
        final String promotionType = promotionProperties.type().getName();
        return positions.stream()
                .map(position -> toRow(position, promotionType))
                .toList();
    }

    private static ReportRow toRow(final ForecastPosition position, final String promotionType) {
        final PromotionPosition actual = position.actualPosition();
        final Optional<PromotionPosition> donor = Optional.ofNullable(position.alignedPosition());
        return new ReportRow(
                actual.manager(),
                actual.storeName(),
                actual.storeFormat(),
                actual.productId(),
                actual.productName(),
                actual.thirdSubcategoryName(),
                promotionType,
                actual.startDate(),
                actual.endDate(),
                actual.carryover(),
                position.algorithmLevel().ordinal(),
                donorValue(donor, PromotionPosition::storeName),
                donorValue(donor, PromotionPosition::storeFormat),
                donorValue(donor, PromotionPosition::startDate),
                donorValue(donor, PromotionPosition::endDate),
                donorValue(donor, PromotionPosition::soldUnits),
                donorValue(donor, PromotionPosition::daysOnStock),
                position.averageDailySales(),
                position.forecast().setScale(0, RoundingMode.HALF_UP).longValueExact()
        );
    }

    private static <T> T donorValue(final Optional<PromotionPosition> donor,
                                    final Function<PromotionPosition, T> getter) {
        return donor.map(getter).orElse(null);
    }
}
