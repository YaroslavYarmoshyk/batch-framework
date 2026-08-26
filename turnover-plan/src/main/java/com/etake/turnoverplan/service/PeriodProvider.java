package com.etake.turnoverplan.service;

import com.etake.turnoverplan.config.properties.SystemConfigurationProperties;
import com.etake.turnoverplan.model.Quarter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class PeriodProvider {
    private final SystemConfigurationProperties systemConfigurationProperties;

    public YearMonth getCurrentYearMonth() {
        return YearMonth.of(
                systemConfigurationProperties.plannedYear(),
                systemConfigurationProperties.plannedMonth()
        ).minusMonths(1);
    }

    public static YearMonth getYearMonth(final YearMonth yearMonth,
                                         final Integer yearOffset,
                                         final Integer monthOffset) {
        return yearMonth.plusYears(yearOffset).plusMonths(monthOffset);
    }

    public Quarter getPlannedQuarter() {
        return getQuarter(0);
    }

    public Quarter getPrevQuarter() {
        return getQuarter(-1);
    }

    private Quarter getQuarter(final Integer yearOffset) {
        int i = 0;
        final String actualMonthName = getOffsetMontYearName(yearOffset, i++);
        final String firstMonthName = getOffsetMontYearName(yearOffset, i++);
        final String secondMonthName = getOffsetMontYearName(yearOffset, i++);
        final String thirdMonthName = getOffsetMontYearName(yearOffset, i);
        return new Quarter(actualMonthName, firstMonthName, secondMonthName, thirdMonthName);
    }

    private String getOffsetMontYearName(final Integer yearOffset, final Integer monthOffset) {
        final YearMonth yearMonth = getCurrentYearMonth().plusYears(yearOffset).plusMonths(monthOffset);

        return String.format("%s - %s", getMonthPart(yearMonth.getMonthValue()), yearMonth.format(DateTimeFormatter.ofPattern("yy")));
    }

    private static String getMonthPart(final Integer month) {
        return switch (month) {
            case 1 -> "січ";
            case 2 -> "лют";
            case 3 -> "бер";
            case 4 -> "кві";
            case 5 -> "тра";
            case 6 -> "чер";
            case 7 -> "лип";
            case 8 -> "сер";
            case 9 -> "вер";
            case 10 -> "жов";
            case 11 -> "лис";
            case 12 -> "гру";
            default -> throw new IllegalStateException("Cannot resolve month name");
        };
    }
}
