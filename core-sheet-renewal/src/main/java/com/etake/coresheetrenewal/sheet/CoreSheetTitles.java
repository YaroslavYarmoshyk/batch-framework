package com.etake.coresheetrenewal.sheet;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CoreSheetTitles {
    private static final Locale UKRAINIAN = Locale.of("uk");

    /** E.g. {@code Листопад 26}. */
    public static String of(final YearMonth month) {
        final String monthName = month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, UKRAINIAN);
        return "%s %02d".formatted(StringUtils.capitalize(monthName), month.getYear() % 100);
    }
}
