package com.excel.custom.library.util;

import org.apache.poi.ss.util.CellReference;

public final class FormulasUtils {
    public static final String IF = "IF";
    public static final String AND = "AND";
    public static final String OR = "OR";
    public static final String MAX = "MAX";
    public static final String ROUND = "ROUND";
    public static final String SUM = "SUM";

    public static String getRange(final int startRow, final int startColumn, final int endRow, final int endColumn) {
        final String from = new CellReference(startRow, startColumn).formatAsString();
        final String to = new CellReference(endRow, endColumn).formatAsString();
        return from + ":" + to;
    }

    public static String getRange(final int row, final int column) {
        return new CellReference(row, column).formatAsString();
    }

    public static String getCyclicActionCoefficientFormula(final String beforeAverageSalesCell,
                                                           final String actionAverageSalesCell,
                                                           final int maxCoefficient,
                                                           final int defaultCoefficient) {
        return IF + "(" + AND + "(" + actionAverageSalesCell + "<>0," +
                beforeAverageSalesCell + "<>0)," + IF + "(" + actionAverageSalesCell +
                "/" + beforeAverageSalesCell + "-1>" + maxCoefficient + "," +
                maxCoefficient + "," + IF + "(" + actionAverageSalesCell +
                "/" + beforeAverageSalesCell + "-1<" + defaultCoefficient + "," +
                defaultCoefficient + "," + actionAverageSalesCell + "/" + beforeAverageSalesCell +
                "-1)),0)";
    }

    public static String getCyclicForecastAvgFormula(final String beforeAverageSalesCell,
                                                     final String actionAverageSalesCell,
                                                     final String actualAverageSalesCell,
                                                     final String dynamicCoefficientCell,
                                                     final int defaultCoefficient) {
        final String resultFormula = IF + "(" + dynamicCoefficientCell + "=0," + IF + "(" + OR + "(" + actionAverageSalesCell
                + "=0," + MAX + "(" + beforeAverageSalesCell + ":" + actualAverageSalesCell + ")>" + actionAverageSalesCell
                + ")," + MAX + "(" + beforeAverageSalesCell + ":" + actualAverageSalesCell + ")*" + defaultCoefficient
                + "," + actionAverageSalesCell + ")," + IF + "(" + actualAverageSalesCell + "=0," + MAX + "(" + actionAverageSalesCell
                + ":" + beforeAverageSalesCell + ")," + dynamicCoefficientCell + "*" + actualAverageSalesCell + "))";
        return IF + "(" + resultFormula + "=0,1," + resultFormula + ")";
    }

    public static String getCyclicForecastFormula(final String forecastAvgSalesCell,
                                                  final String carryOverCell,
                                                  final int demandDays) {
        return ROUND + "(" + forecastAvgSalesCell + "*" + demandDays + "+" + carryOverCell + ",0)";
    }

    public static String getSum(final String... cells) {
        final StringBuilder stringBuilder = new StringBuilder(SUM).append("(");
        for (int i = 0; i < cells.length; i++) {
            if (i == cells.length - 1) {
                stringBuilder.append(cells[i]).append(")");
                break;
            }
            stringBuilder.append(cells[i]).append(",");
        }
        return stringBuilder.toString();
    }
}
