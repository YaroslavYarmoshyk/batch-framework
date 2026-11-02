package com.etake.promotionforecast.mapper;

import com.etake.promotionforecast.config.properties.PromotionProperties;
import com.etake.promotionforecast.model.ForecastPosition;
import com.etake.promotionforecast.model.PromotionPosition;
import com.etake.promotionforecast.model.ReportRow;
import com.etake.promotionforecast.model.enumeration.AlgorithmLevel;
import com.etake.promotionforecast.model.enumeration.PromotionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRowMapperTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 14);

    private final ReportRowMapper mapper = new ReportRowMapper(
            new PromotionProperties(PromotionType.CYCLIC, START, END, Period.ofYears(2), 4));

    @Test
    void mapsDonorFieldsAndRoundsForecastHalfUp() {
        final PromotionPosition actual = position("S1", "P1", START, END, 3, 0L, 0L);
        final PromotionPosition donor = position("S1", "P1", START.minusYears(1), END.minusYears(1), 0, 21L, 6L);
        final ForecastPosition forecast = new ForecastPosition(
                actual, donor, AlgorithmLevel.LEVEL_1, new BigDecimal("3.5"), new BigDecimal("52.5"));

        final ReportRow row = mapper.map(List.of(forecast)).getFirst();

        assertThat(row.manager()).isEqualTo("Manager P1");
        assertThat(row.storeName()).isEqualTo("Store S1");
        assertThat(row.productId()).isEqualTo("P1");
        assertThat(row.subcategory()).isEqualTo("3-Маслини та оливки");
        assertThat(row.promotionType()).isEqualTo(PromotionType.CYCLIC.getName());
        assertThat(row.startDate()).isEqualTo(START);
        assertThat(row.carryover()).isEqualTo(3);
        assertThat(row.algorithmLevel()).isEqualTo(1);
        assertThat(row.donorStore()).isEqualTo("Store S1");
        assertThat(row.donorStartDate()).isEqualTo(START.minusYears(1));
        assertThat(row.donorSoldUnits()).isEqualTo(21L);
        assertThat(row.donorDaysOnStock()).isEqualTo(6L);
        assertThat(row.avgDailySales()).isEqualByComparingTo("3.5");
        assertThat(row.forecastQuantity()).isEqualTo(53L);
    }

    @Test
    void leavesDonorFieldsEmptyWhenNoAlignmentFound() {
        final PromotionPosition actual = position("S2", "P2", START, END, 0, 0L, 0L);
        final ForecastPosition forecast = new ForecastPosition(
                actual, null, AlgorithmLevel.NONE, BigDecimal.ONE, new BigDecimal("14"));

        final ReportRow row = mapper.map(List.of(forecast)).getFirst();

        assertThat(row.algorithmLevel()).isZero();
        assertThat(row.donorStore()).isNull();
        assertThat(row.donorFormat()).isNull();
        assertThat(row.donorStartDate()).isNull();
        assertThat(row.donorEndDate()).isNull();
        assertThat(row.donorSoldUnits()).isNull();
        assertThat(row.donorDaysOnStock()).isNull();
        assertThat(row.forecastQuantity()).isEqualTo(14L);
    }

    private static PromotionPosition position(final String storeId, final String productId,
                                              final LocalDate start, final LocalDate end,
                                              final int carryover, final long soldUnits, final long daysOnStock) {
        return new PromotionPosition("promo-" + storeId, "Manager " + productId, storeId, "Store " + storeId, productId, "Product " + productId,
                "SUB3", "3-Маслини та оливки", carryover, "1", start, end, soldUnits, daysOnStock);
    }
}
