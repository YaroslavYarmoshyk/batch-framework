package com.etake.promotionforecast.writer.googlesheets;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SheetCells {
    /** Day zero of the Sheets date serial numbers. */
    private static final LocalDate SERIAL_EPOCH = LocalDate.of(1899, 12, 30);

    /**
     * Converts a report value into a cell value for {@code USER_ENTERED} input: ISO dates are parsed into
     * typed dates regardless of the spreadsheet locale, numbers stay numbers, nulls become empty cells.
     */
    public static Object toCellValue(final Object value) {
        return switch (value) {
            case null -> "";
            case LocalDate date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case BigDecimal decimal -> decimal.doubleValue();
            default -> value;
        };
    }

    public static long toSerialNumber(final LocalDate date) {
        return ChronoUnit.DAYS.between(SERIAL_EPOCH, date);
    }

    public static boolean isBlank(final Object value) {
        return value == null || value.toString().isBlank();
    }

    /** Sheet names containing spaces, non-ASCII characters or quotes must be single-quoted in A1 notation. */
    public static String quote(final String sheetName) {
        return "'" + sheetName.replace("'", "''") + "'";
    }

    public static String a1(final String sheetName, final String range) {
        return quote(sheetName) + "!" + range;
    }
}
