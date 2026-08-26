package com.etake.turnoverplan.utils;

import com.etake.turnoverplan.config.indices.DataSheetColumnIndices;
import com.etake.turnoverplan.model.RegionRowInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellReference;

import java.util.List;
import java.util.stream.IntStream;

import static com.etake.turnoverplan.utils.Constants.DATA_SHEET_NAME;

@NoArgsConstructor(access = AccessLevel.NONE)
public final class ExcelFormulaUtils {

    public static String getDynFormula(final DataSheetColumnIndices dataSheetColumnIndices,
                                       final Integer numeratorColumnIndex,
                                       final Integer denominatorColumnIndex) {
        final Integer storeRangeColumnIndex = dataSheetColumnIndices.store();
        final Integer storeCriteriaColumnIndex = dataSheetColumnIndices.similarStore();
        final Integer categoryColumnIndex = dataSheetColumnIndices.category();

        final String numeratorPart = "SUMIFS(%s,%s,%s,%s,%s)".formatted(
                getFullRangeByColumnIndex(numeratorColumnIndex),
                getFullRangeByColumnIndex(storeRangeColumnIndex),
                getCurrentRowRangeByColumnIndex(storeCriteriaColumnIndex),
                getFullRangeByColumnIndex(categoryColumnIndex),
                getCurrentRowRangeByColumnIndex(categoryColumnIndex)
        );
        final String denominatorPart = "SUMIFS(%s,%s,%s,%s,%s)".formatted(
                getFullRangeByColumnIndex(denominatorColumnIndex),
                getFullRangeByColumnIndex(storeRangeColumnIndex),
                getCurrentRowRangeByColumnIndex(storeCriteriaColumnIndex),
                getFullRangeByColumnIndex(categoryColumnIndex),
                getCurrentRowRangeByColumnIndex(categoryColumnIndex)
        );
        return "IF(OR(%s = 0, %s = 0), 0, %s / %s - 1)".formatted(numeratorPart, denominatorPart, numeratorPart, denominatorPart);
    }

    public static String getAvgPlanFormula(final Integer avgColumnIndex, final Integer dynColumnIndex) {
        return "%s*(1+%s)".formatted(getCurrentRowRangeByColumnIndex(avgColumnIndex), getCurrentRowRangeByColumnIndex(dynColumnIndex));
    }

    public static String getDataPlanFormula(final DataSheetColumnIndices dataSheetColumnIndices,
                                            final Integer avgPlanColumnIndex,
                                            final Integer daysColumnIndex) {
        final Integer storeRangeColumnIndex = dataSheetColumnIndices.store();
        final Integer storeCriteriaColumnIndex = dataSheetColumnIndices.similarStore();
        final Integer categoryColumnIndex = dataSheetColumnIndices.category();
        final String daysFormulaPart = "SUMIFS(%s,%s,%s,%s,%s)".formatted(
                getFullRangeByColumnIndex(daysColumnIndex),
                getFullRangeByColumnIndex(storeRangeColumnIndex),
                getCurrentRowRangeByColumnIndex(storeCriteriaColumnIndex),
                getFullRangeByColumnIndex(categoryColumnIndex),
                getCurrentRowRangeByColumnIndex(categoryColumnIndex)
        );
        return "%s*%s".formatted(getCurrentRowRangeByColumnIndex(avgPlanColumnIndex), daysFormulaPart);
    }

    public static String getPlanSumFormula(final Integer dataSheetSumColumnIndex,
                                           final Integer dataSheetCriteriaColumnIndex,
                                           final Integer criteriaColumnIndex) {
        final String criteriaRange = "%s!%s".formatted(DATA_SHEET_NAME, getFullRangeByColumnIndex(dataSheetSumColumnIndex));
        final String criteria = getCurrentRowRangeByColumnIndex(criteriaColumnIndex);
        final String sumRange = "%s!%s".formatted(DATA_SHEET_NAME, getFullRangeByColumnIndex(dataSheetCriteriaColumnIndex));
        return "SUMIF(%s,%s,%s)".formatted(criteriaRange, criteria, sumRange);
    }

    public static String getAdjustedMarginStoresFormula(final Integer marginColumnIndex,
                                                        final Integer turnoverColumnIndex,
                                                        final Integer adjustedTurnoverColumnIndex) {
        return "IF(%s=\"\",\"\",%s*(%s/%s))".formatted(
                getCurrentRowRangeByColumnIndex(adjustedTurnoverColumnIndex),
                getCurrentRowRangeByColumnIndex(adjustedTurnoverColumnIndex),
                getCurrentRowRangeByColumnIndex(marginColumnIndex),
                getCurrentRowRangeByColumnIndex(turnoverColumnIndex)
        );
    }

    public static String getTotalSumPerRegionFormula(final int startRowIndex, final int endRowIndex) {
        return "SUBTOTAL(9, INDIRECT(SUBSTITUTE(ADDRESS(ROW(), COLUMN(), 4), ROW(), \"\") & \"%d:\" & SUBSTITUTE(ADDRESS(ROW(), COLUMN(), 4), ROW(), \"\") & \"%d\"))"
                .formatted(startRowIndex + 1, endRowIndex + 1);
    }

    public static String getOverallSumFormula(final List<RegionRowInfo> rowInfo) {
        final StringBuilder stringBuilder = new StringBuilder("SUM(");
        for (int i = 0; i < rowInfo.size(); i++) {
            final RegionRowInfo regionRowInfo = rowInfo.get(i);
            stringBuilder.append("INDIRECT(ADDRESS(%d, COLUMN(), 4))".formatted(regionRowInfo.regionRowIndex() + 1));
            if (i != rowInfo.size()) {
                stringBuilder.append(", ");
            }
        }
        stringBuilder.append(")");
        return stringBuilder.toString();
    }

    public static void setFormula(final String formula, final Sheet sheet, final Integer startRow,
                                  final Integer endRow, final Integer startColumn, final Integer endColumn) {
        for (int i = startRow; i <= endRow; i++) {
            for (int j = startColumn; j <= endColumn; j++) {
                sheet.getRow(i).getCell(j).setCellFormula(formula);
            }
        }
    }

    public static void setFormula(final String formula, final Sheet sheet, final Integer startRow,
                                  final Integer endRow, final Integer column) {
        IntStream.range(startRow, endRow)
                .forEach(i -> sheet.getRow(i).getCell(column).setCellFormula(formula));
    }

    private static String getFullRangeByColumnIndex(final Integer columnIndex) {
        final String columnName = CellReference.convertNumToColString(columnIndex);
        return "%s:%s".formatted(columnName, columnName);
    }

    private static String getCurrentRowRangeByColumnIndex(final Integer columnIndex) {
        final String columnName = CellReference.convertNumToColString(columnIndex);
        return "INDIRECT(\"%s\"&ROW())".formatted(columnName);
    }
}
