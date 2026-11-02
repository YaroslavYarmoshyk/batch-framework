package com.etake.promotionforecast.service;

import com.etake.promotionforecast.config.properties.PromotionProperties;
import com.etake.promotionforecast.model.enumeration.PromotionType;
import com.etake.promotionforecast.repository.PromotionPositionsRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionPositionsServiceTest {
    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 10, 14);

    private final PromotionPositionsRepository repository = mock(PromotionPositionsRepository.class);

    @Test
    void throwsWhenPromotionDoesNotExist() {
        when(repository.existsPromotion(PromotionType.CYCLIC, START, END)).thenReturn(false);

        assertThatThrownBy(() -> service(START, END).forecast())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No 'Циклічна акція' promotion found");
        verify(repository, never()).getPromotionPositions(any(), any(), any());
    }

    @Test
    void throwsWhenStartDateIsAfterEndDate() {
        assertThatThrownBy(() -> service(END, START).forecast())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is after end date");
        verify(repository, never()).existsPromotion(any(), any(), any());
        verify(repository, never()).getPromotionPositions(any(), any(), any());
    }

    @Test
    void throwsWhenDateIsMissing() {
        assertThatThrownBy(() -> service(START, null).forecast())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be provided");
        verify(repository, never()).getPromotionPositions(any(), any(), any());
    }

    @Test
    void runsForecastQueryWhenPromotionExists() {
        when(repository.existsPromotion(PromotionType.CYCLIC, START, END)).thenReturn(true);
        when(repository.getPromotionPositions(any(), any(), any())).thenReturn(List.of());

        assertThat(service(START, END).forecast()).isEmpty();
        verify(repository).getPromotionPositions(PromotionType.CYCLIC, END.minus(Period.ofYears(2)), END);
    }

    private PromotionPositionsService service(LocalDate startDate, LocalDate endDate) {
        PromotionProperties properties = new PromotionProperties(
                PromotionType.CYCLIC, startDate, endDate, Period.ofYears(2), 4);
        return new PromotionPositionsService(repository, properties);
    }
}
