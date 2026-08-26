package com.etake.salesplan.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExcelFormulaUtils {

    public static String getSumPerDateFormula(int sumColNum, int criteriaColNum) {
        return """
                SUMIFS(
                    INDIRECT("data!"&%s), INDIRECT("data!"&%s), INDIRECT("A"&ROW()), INDIRECT("data!D:D"), INDIRECT(ADDRESS(1, COLUMN(), 4))
                )
                """.formatted(getColumnNameFormula(sumColNum), getColumnNameFormula(criteriaColNum));
    }

    public static String getTotalSumPerParentFormula(final int startRowIndex, final int endRowIndex) {
        return "SUBTOTAL(9, INDIRECT(SUBSTITUTE(ADDRESS(ROW(), COLUMN(), 4), ROW(), \"\") & \"%d:\" & SUBSTITUTE(ADDRESS(ROW(), COLUMN(), 4), ROW(), \"\") & \"%d\"))"
                .formatted(startRowIndex + 1, endRowIndex);
    }

    public static String getTotalSumFormula(List<Integer> regionRows) {
        StringBuilder stringBuilder = new StringBuilder("SUM(");
        for (Integer regionRow : regionRows) {
            String regionCell = """
                     INDIRECT(ADDRESS(%d, COLUMN(), 4)), \s
                    \s""".formatted(regionRow);
            stringBuilder.append(regionCell);
        }
        stringBuilder.append(")");
        return stringBuilder.toString();
    }

    private static String getColumnNameFormula(int number) {
        return """
                CHAR(%d+64)&":"&CHAR(%d+64)
                """.formatted(number, number);
    }
}
