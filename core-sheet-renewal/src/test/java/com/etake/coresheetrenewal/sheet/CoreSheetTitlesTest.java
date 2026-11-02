package com.etake.coresheetrenewal.sheet;

import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class CoreSheetTitlesTest {

    @Test
    void usesTheUkrainianMonthNameAndTwoDigitYear() {
        assertThat(CoreSheetTitles.of(YearMonth.of(2026, 10))).isEqualTo("Жовтень 26");
        assertThat(CoreSheetTitles.of(YearMonth.of(2026, 11))).isEqualTo("Листопад 26");
        assertThat(CoreSheetTitles.of(YearMonth.of(2027, 1))).isEqualTo("Січень 27");
    }

    @Test
    void columnIndexesMatchTheirLetters() {
        assertThat(CoreSheetColumn.SALES_PLAN.index()).isEqualTo(2);
        assertThat(CoreSheetColumn.FIRST_DAY.index()).isEqualTo(34);
        assertThat(CoreSheetColumn.LAST_DAY.index()).isEqualTo(64);
        assertThat(CoreSheetColumn.DAYS.index()).isEqualTo(66);
    }
}
