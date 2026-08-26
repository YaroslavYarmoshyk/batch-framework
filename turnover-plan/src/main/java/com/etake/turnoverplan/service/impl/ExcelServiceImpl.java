package com.etake.turnoverplan.service.impl;

import com.etake.turnoverplan.config.indices.CategoriesSheetColumnIndices;
import com.etake.turnoverplan.config.indices.DataSheetColumnIndices;
import com.etake.turnoverplan.config.indices.ResultSheetColumnIndices;
import com.etake.turnoverplan.config.indices.StoresSheetColumnIndices;
import com.etake.turnoverplan.config.properties.ColumnIndices;
import com.etake.turnoverplan.model.Quarter;
import com.etake.turnoverplan.model.RegionOrder;
import com.etake.turnoverplan.model.RegionRowInfo;
import com.etake.turnoverplan.model.StoreCategorySales;
import com.etake.turnoverplan.repository.RegionRepository;
import com.etake.turnoverplan.service.ExcelFormatService;
import com.etake.turnoverplan.service.ExcelService;
import com.etake.turnoverplan.service.PeriodProvider;
import com.etake.turnoverplan.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.etake.turnoverplan.utils.Constants.CATEGORY;
import static com.etake.turnoverplan.utils.Constants.CATEGORY_SHEET_NAME;
import static com.etake.turnoverplan.utils.Constants.DATA_SHEET_NAME;
import static com.etake.turnoverplan.utils.Constants.FIRST_ROW_INDEX;
import static com.etake.turnoverplan.utils.Constants.INITIAL_VALUE_ROW_INDEX;
import static com.etake.turnoverplan.utils.Constants.MARGIN_COLUMN_NAME;
import static com.etake.turnoverplan.utils.Constants.SECOND_ROW_INDEX;
import static com.etake.turnoverplan.utils.Constants.SIMILAR_STORE;
import static com.etake.turnoverplan.utils.Constants.STORE;
import static com.etake.turnoverplan.utils.Constants.STORES_SHEET_NAME;
import static com.etake.turnoverplan.utils.Constants.TOTAL;
import static com.etake.turnoverplan.utils.Constants.TURNOVER_COLUMN_NAME;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getAdjustedMarginStoresFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getAvgPlanFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getDataPlanFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getDynFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getOverallSumFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getPlanSumFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.getTotalSumPerRegionFormula;
import static com.etake.turnoverplan.utils.ExcelFormulaUtils.setFormula;
import static com.etake.turnoverplan.utils.ExcelUtils.applyCellStyle;
import static com.etake.turnoverplan.utils.ExcelUtils.createCells;
import static com.etake.turnoverplan.utils.ExcelUtils.setValue;
import static java.lang.String.format;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Service
@RequiredArgsConstructor
public class ExcelServiceImpl implements ExcelService {
    private final RegionRepository regionRepository;
    private final PeriodProvider periodProvider;
    private final ColumnIndices columnIndices;
    private final ExcelFormatService excelFormatService;

    @Override
    public Workbook getWorkbook(final Collection<StoreCategorySales> sales) {
        final Workbook workbook = new XSSFWorkbook();
        final Quarter prevQuarter = periodProvider.getPrevQuarter();
        final Quarter plannedQuarter = periodProvider.getPlannedQuarter();
        final List<RegionOrder> regionOrders = regionRepository.findAllRegionOrders();

        final Sheet data = getDataSheet(workbook, prevQuarter, plannedQuarter, sales);
        final Sheet categoriesPlan = getCategoriesSheet(workbook, plannedQuarter, sales);
        final Sheet storesPlan = createStoresSheet(workbook, plannedQuarter, sales, regionOrders);
        final List<String> regions = regionOrders.stream()
                .map(RegionOrder::name)
                .toList();
        excelFormatService.formatDataSheet(workbook, data);
        excelFormatService.formatCategoriesSheet(workbook, categoriesPlan);
        excelFormatService.formatStoresSheet(workbook, storesPlan, regions);

        workbook.setActiveSheet(workbook.getSheetIndex(STORES_SHEET_NAME));
        return workbook;
    }

    @Override
    public void writeWorkbook(final Workbook workbook, final String path) throws Exception {
        final String fileName = resolveFileName(periodProvider.getPlannedQuarter());
        final File directory = ResourcePaths.resolve(path);
        directory.mkdirs();
        final FileOutputStream outputStream = new FileOutputStream(new File(directory, fileName));
        workbook.write(outputStream);
        workbook.close();
    }

    private Sheet getDataSheet(final Workbook workbook,
                               final Quarter prevQuarter,
                               final Quarter plannedQuarter,
                               final Collection<StoreCategorySales> sales) {
        final DataSheetColumnIndices dataSheetColumnIndices = columnIndices.dataSheet();
        final Sheet dataSheet = workbook.createSheet(DATA_SHEET_NAME);

        createCells(dataSheet, INITIAL_VALUE_ROW_INDEX + sales.size(), 33);

        createDataSheetHeaders(workbook, dataSheet, dataSheetColumnIndices, prevQuarter, plannedQuarter);
        populateCalculatedData(dataSheet, dataSheetColumnIndices, sales);
        populateDataSheetFormulas(dataSheet, dataSheetColumnIndices, sales.size());

        return dataSheet;
    }

    private static void createDataSheetHeaders(final Workbook workbook, final Sheet dataSheet,
                                               final DataSheetColumnIndices dataSheetColumnIndices,
                                               final Quarter prevQuarter, final Quarter plannedQuarter) {
        final Row firstRow = dataSheet.getRow(FIRST_ROW_INDEX);
        final Row secondRow = dataSheet.getRow(SECOND_ROW_INDEX);
        firstRow.getCell(0).setCellValue(STORE);
        firstRow.getCell(1).setCellValue(SIMILAR_STORE);
        firstRow.getCell(2).setCellValue(CATEGORY);

        final int storeColumnIndex = dataSheetColumnIndices.store();
        final int similarStoreColumnIndex = dataSheetColumnIndices.similarStore();
        final int categoryColumnIndex = dataSheetColumnIndices.category();
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, storeColumnIndex, storeColumnIndex));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, similarStoreColumnIndex, similarStoreColumnIndex));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, categoryColumnIndex, categoryColumnIndex));

        final int avgTurnoverPrevPeriodActualMonthColumnIndex = dataSheetColumnIndices.avgTurnoverPrevPeriodActualMonth();
        final int avgMarginPrevPeriodActualMonthColumnIndex = dataSheetColumnIndices.avgMarginPrevPeriodActualMonth();
        firstRow.getCell(avgTurnoverPrevPeriodActualMonthColumnIndex).setCellValue(format("СДП %s", prevQuarter.actualMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, avgTurnoverPrevPeriodActualMonthColumnIndex, avgMarginPrevPeriodActualMonthColumnIndex));
        final int avgTurnoverPrevPeriodFirstMonthColumnIndex = dataSheetColumnIndices.avgTurnoverPrevPeriodFirstMonth();
        final int avgMarginPrevPeriodFirstMonthColumnIndex = dataSheetColumnIndices.avgMarginPrevPeriodFirstMonth();
        firstRow.getCell(avgTurnoverPrevPeriodFirstMonthColumnIndex).setCellValue(format("СДП %s", prevQuarter.firstMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, avgTurnoverPrevPeriodFirstMonthColumnIndex, avgMarginPrevPeriodFirstMonthColumnIndex));
        final int avgTurnoverPrevPeriodSecondMonthColumnIndex = dataSheetColumnIndices.avgTurnoverPrevPeriodSecondMonth();
        final int avgMarginPrevPeriodSecondMonthColumnIndex = dataSheetColumnIndices.avgMarginPrevPeriodSecondMonth();
        firstRow.getCell(avgTurnoverPrevPeriodSecondMonthColumnIndex).setCellValue(format("СДП %s", prevQuarter.secondMonth()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, avgTurnoverPrevPeriodSecondMonthColumnIndex, avgMarginPrevPeriodSecondMonthColumnIndex));
        final int avgTurnoverPrevPeriodThirdMonthColumnIndex = dataSheetColumnIndices.avgTurnoverPrevPeriodThirdMonth();
        final int avgMarginPrevPeriodThirdMonthColumnIndex = dataSheetColumnIndices.avgMarginPrevPeriodThirdMonth();
        firstRow.getCell(avgTurnoverPrevPeriodThirdMonthColumnIndex).setCellValue(format("СДП %s", prevQuarter.thirdMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, avgTurnoverPrevPeriodThirdMonthColumnIndex, avgMarginPrevPeriodThirdMonthColumnIndex));
        final int avgTurnoverCurrentPeriodActualMonthColumnIndex = dataSheetColumnIndices.avgTurnoverCurrentPeriodActualMonth();
        final int avgMarginCurrentPeriodActualMonthColumnIndex = dataSheetColumnIndices.avgMarginCurrentPeriodActualMonth();
        firstRow.getCell(avgTurnoverCurrentPeriodActualMonthColumnIndex).setCellValue(format("СДП %s", plannedQuarter.actualMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, avgTurnoverCurrentPeriodActualMonthColumnIndex, avgMarginCurrentPeriodActualMonthColumnIndex));

        final int dynTurnoverFirstToActualMonthColumnIndex = dataSheetColumnIndices.dynTurnoverFirstToActualMonth();
        final int dynMarginFirstToActualMonthColumnIndex = dataSheetColumnIndices.dynMarginFirstToActualMonth();
        firstRow.getCell(dynTurnoverFirstToActualMonthColumnIndex).setCellValue(format("Дин %s / %s", prevQuarter.firstMonthName(), prevQuarter.actualMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, dynTurnoverFirstToActualMonthColumnIndex, dynMarginFirstToActualMonthColumnIndex));
        final int dynTurnoverSecondToFirstMonthColumnIndex = dataSheetColumnIndices.dynTurnoverSecondToFirstMonth();
        final int dynMarginSecondToFirstMonthColumnIndex = dataSheetColumnIndices.dynMarginSecondToFirstMonth();
        firstRow.getCell(dynTurnoverSecondToFirstMonthColumnIndex).setCellValue(format("Дин %s / %s", prevQuarter.secondMonth(), prevQuarter.firstMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, dynTurnoverSecondToFirstMonthColumnIndex, dynMarginSecondToFirstMonthColumnIndex));
        final int dynTurnoverThirdToSecondMonthColumnIndex = dataSheetColumnIndices.dynTurnoverThirdToSecondMonth();
        final int dynMarginThirdToSecondMonthColumnIndex = dataSheetColumnIndices.dynMarginThirdToSecondMonth();
        firstRow.getCell(dynTurnoverThirdToSecondMonthColumnIndex).setCellValue(format("Дин %s / %s", prevQuarter.thirdMonthName(), prevQuarter.secondMonth()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, dynTurnoverThirdToSecondMonthColumnIndex, dynMarginThirdToSecondMonthColumnIndex));

        final int planAvgTurnoverFirstMonthColumnIndex = dataSheetColumnIndices.planAvgTurnoverFirstMonth();
        final int planAvgMarginFirstMonthColumnIndex = dataSheetColumnIndices.planAvgMarginFirstMonth();
        firstRow.getCell(planAvgTurnoverFirstMonthColumnIndex).setCellValue(format("План СДП %s", plannedQuarter.firstMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planAvgTurnoverFirstMonthColumnIndex, planAvgMarginFirstMonthColumnIndex));
        final int planAvgTurnoverSecondMonthColumnIndex = dataSheetColumnIndices.planAvgTurnoverSecondMonth();
        final int planAvgMarginSecondMonthColumnIndex = dataSheetColumnIndices.planAvgMarginSecondMonth();
        firstRow.getCell(planAvgTurnoverSecondMonthColumnIndex).setCellValue(format("План СДП %s", plannedQuarter.secondMonth()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planAvgTurnoverSecondMonthColumnIndex, planAvgMarginSecondMonthColumnIndex));
        final int planAvgTurnoverThirdMonthColumnIndex = dataSheetColumnIndices.planAvgTurnoverThirdMonth();
        final int planAvgMarginThirdMonthColumnIndex = dataSheetColumnIndices.planAvgMarginThirdMonth();
        firstRow.getCell(planAvgTurnoverThirdMonthColumnIndex).setCellValue(format("План СДП %s", plannedQuarter.thirdMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planAvgTurnoverThirdMonthColumnIndex, planAvgMarginThirdMonthColumnIndex));

        final int daysFirstMonthColumnIndex = dataSheetColumnIndices.daysFirstMonth();
        final int daysSecondMonthColumnIndex = dataSheetColumnIndices.daysSecondMonth();
        final int daysThirdMonthColumnIndex = dataSheetColumnIndices.daysThirdMonth();
        firstRow.getCell(daysFirstMonthColumnIndex).setCellValue(format("Дні %s", plannedQuarter.firstMonthName()));
        firstRow.getCell(daysSecondMonthColumnIndex).setCellValue(format("Дні %s", plannedQuarter.secondMonth()));
        firstRow.getCell(daysThirdMonthColumnIndex).setCellValue(format("Дні %s", plannedQuarter.thirdMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, daysFirstMonthColumnIndex, daysFirstMonthColumnIndex));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, daysSecondMonthColumnIndex, daysSecondMonthColumnIndex));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, daysThirdMonthColumnIndex, daysThirdMonthColumnIndex));

        final int planTurnoverFirstMonthColumnIndex = dataSheetColumnIndices.planTurnoverFirstMonth();
        final int planMarginFirstMonthColumnIndex = dataSheetColumnIndices.planMarginFirstMonth();
        firstRow.getCell(planTurnoverFirstMonthColumnIndex).setCellValue(format("План %s", plannedQuarter.firstMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planTurnoverFirstMonthColumnIndex, planMarginFirstMonthColumnIndex));
        final int planTurnoverSecondMonthColumnIndex = dataSheetColumnIndices.planTurnoverSecondMonth();
        final int planMarginSecondMonthColumnIndex = dataSheetColumnIndices.planMarginSecondMonth();
        firstRow.getCell(planTurnoverSecondMonthColumnIndex).setCellValue(format("План %s", plannedQuarter.secondMonth()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planTurnoverSecondMonthColumnIndex, planMarginSecondMonthColumnIndex));
        final int planTurnoverThirdMonthColumnIndex = dataSheetColumnIndices.planTurnoverThirdMonth();
        final int planMarginThirdMonthColumnIndex = dataSheetColumnIndices.planMarginThirdMonth();
        firstRow.getCell(planTurnoverThirdMonthColumnIndex).setCellValue(format("План %s", plannedQuarter.thirdMonthName()));
        dataSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planTurnoverThirdMonthColumnIndex, planMarginThirdMonthColumnIndex));

        dataSheetColumnIndices.getTurnoverColumnIndices().forEach(i -> secondRow.getCell(i).setCellValue(TURNOVER_COLUMN_NAME));
        dataSheetColumnIndices.getMarginColumnIndices().forEach(i -> secondRow.getCell(i).setCellValue(MARGIN_COLUMN_NAME));

        final CellStyle centeredWrappedStyle = workbook.createCellStyle();
        centeredWrappedStyle.setAlignment(HorizontalAlignment.CENTER);
        centeredWrappedStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        centeredWrappedStyle.setWrapText(true);

        applyCellStyle(dataSheet, centeredWrappedStyle, 0, 0, 1, 33);
    }

    private static void populateCalculatedData(final Sheet dataSheet, final DataSheetColumnIndices dataSheetColumnIndices,
                                               final Collection<StoreCategorySales> sales) {
        int startRow = INITIAL_VALUE_ROW_INDEX;
        for (final StoreCategorySales storeCategorySales : sales) {
            final Row workRow = dataSheet.getRow(startRow++);
            setValue(workRow, dataSheetColumnIndices.store(), storeCategorySales.getStoreName());
            setValue(workRow, dataSheetColumnIndices.similarStore(), storeCategorySales.getSimilarStore());
            setValue(workRow, dataSheetColumnIndices.category(), storeCategorySales.getCategoryName());

            setValue(workRow, dataSheetColumnIndices.avgTurnoverPrevPeriodActualMonth(), storeCategorySales.getPrevPeriodCurrentMonthAvgTurnover());
            setValue(workRow, dataSheetColumnIndices.avgMarginPrevPeriodActualMonth(), storeCategorySales.getPrevPeriodCurrentMonthAvgMargin());
            setValue(workRow, dataSheetColumnIndices.avgTurnoverPrevPeriodFirstMonth(), storeCategorySales.getPrevPeriodFirstMonthAvgTurnover());
            setValue(workRow, dataSheetColumnIndices.avgMarginPrevPeriodFirstMonth(), storeCategorySales.getPrevPeriodFirstMonthAvgMargin());
            setValue(workRow, dataSheetColumnIndices.avgTurnoverPrevPeriodSecondMonth(), storeCategorySales.getPrevPeriodSecondMonthAvgTurnover());
            setValue(workRow, dataSheetColumnIndices.avgMarginPrevPeriodSecondMonth(), storeCategorySales.getPrevPeriodSecondMonthAvgMargin());
            setValue(workRow, dataSheetColumnIndices.avgTurnoverPrevPeriodThirdMonth(), storeCategorySales.getPrevPeriodThirdMonthAvgTurnover());
            setValue(workRow, dataSheetColumnIndices.avgMarginPrevPeriodThirdMonth(), storeCategorySales.getPrevPeriodThirdMonthAvgMargin());
            setValue(workRow, dataSheetColumnIndices.avgTurnoverCurrentPeriodActualMonth(), storeCategorySales.getCurrentPeriodCurrentMonthAvgTurnover());
            setValue(workRow, dataSheetColumnIndices.avgMarginCurrentPeriodActualMonth(), storeCategorySales.getCurrentPeriodCurrentMonthAvgMargin());

            setValue(workRow, dataSheetColumnIndices.daysFirstMonth(), storeCategorySales.getPrevPeriodFirstMonthSalesDays());
            setValue(workRow, dataSheetColumnIndices.daysSecondMonth(), storeCategorySales.getPrevPeriodSecondMonthSalesDays());
            setValue(workRow, dataSheetColumnIndices.daysThirdMonth(), storeCategorySales.getPrevPeriodThirdMonthSalesDays());
        }
    }

    private static void populateDataSheetFormulas(final Sheet dataSheet, final DataSheetColumnIndices dataSheetColumnIndices,
                                                  final Integer positionsSize) {
        final Integer endRow = INITIAL_VALUE_ROW_INDEX + positionsSize;
        final String firstToActualTurnover = getDynFormula(dataSheetColumnIndices, dataSheetColumnIndices.avgTurnoverPrevPeriodFirstMonth(), dataSheetColumnIndices.avgTurnoverPrevPeriodActualMonth());
        final String secondToFirstTurnover = getDynFormula(dataSheetColumnIndices, dataSheetColumnIndices.avgTurnoverPrevPeriodSecondMonth(), dataSheetColumnIndices.avgTurnoverPrevPeriodFirstMonth());
        final String thirdToSecondTurnover = getDynFormula(dataSheetColumnIndices, dataSheetColumnIndices.avgTurnoverPrevPeriodThirdMonth(), dataSheetColumnIndices.avgTurnoverPrevPeriodSecondMonth());
        final String firstToActualMargin = getDynFormula(dataSheetColumnIndices, dataSheetColumnIndices.avgMarginPrevPeriodFirstMonth(), dataSheetColumnIndices.avgMarginPrevPeriodActualMonth());
        final String secondToFirstMargin = getDynFormula(dataSheetColumnIndices, dataSheetColumnIndices.avgMarginPrevPeriodSecondMonth(), dataSheetColumnIndices.avgMarginPrevPeriodFirstMonth());
        final String thirdToSecondMargin = getDynFormula(dataSheetColumnIndices, dataSheetColumnIndices.avgMarginPrevPeriodThirdMonth(), dataSheetColumnIndices.avgMarginPrevPeriodSecondMonth());
        setFormula(firstToActualTurnover, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.dynTurnoverFirstToActualMonth());
        setFormula(firstToActualMargin, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.dynMarginFirstToActualMonth());
        setFormula(secondToFirstTurnover, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.dynTurnoverSecondToFirstMonth());
        setFormula(secondToFirstMargin, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.dynMarginSecondToFirstMonth());
        setFormula(thirdToSecondTurnover, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.dynTurnoverThirdToSecondMonth());
        setFormula(thirdToSecondMargin, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.dynMarginThirdToSecondMonth());


        setFormula(getAvgPlanFormula(
                dataSheetColumnIndices.avgTurnoverCurrentPeriodActualMonth(),
                dataSheetColumnIndices.dynTurnoverFirstToActualMonth()
        ), dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planAvgTurnoverFirstMonth());
        setFormula(getAvgPlanFormula(
                dataSheetColumnIndices.avgMarginCurrentPeriodActualMonth(),
                dataSheetColumnIndices.dynMarginFirstToActualMonth()
        ), dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planAvgMarginFirstMonth());
        setFormula(getAvgPlanFormula(
                dataSheetColumnIndices.planAvgTurnoverFirstMonth(),
                dataSheetColumnIndices.dynTurnoverSecondToFirstMonth()
        ), dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planAvgTurnoverSecondMonth());
        setFormula(getAvgPlanFormula(
                dataSheetColumnIndices.planAvgMarginFirstMonth(),
                dataSheetColumnIndices.dynMarginSecondToFirstMonth()
        ), dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planAvgMarginSecondMonth());
        setFormula(getAvgPlanFormula(
                dataSheetColumnIndices.planAvgTurnoverSecondMonth(),
                dataSheetColumnIndices.dynTurnoverThirdToSecondMonth()
        ), dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planAvgTurnoverThirdMonth());
        setFormula(getAvgPlanFormula(
                dataSheetColumnIndices.planAvgMarginSecondMonth(),
                dataSheetColumnIndices.dynMarginThirdToSecondMonth()
        ), dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planAvgMarginThirdMonth());

        final String firstMonthPlanTurnover = getDataPlanFormula(dataSheetColumnIndices, dataSheetColumnIndices.planAvgTurnoverFirstMonth(), dataSheetColumnIndices.daysFirstMonth());
        final String secondMonthPlanTurnover = getDataPlanFormula(dataSheetColumnIndices, dataSheetColumnIndices.planAvgTurnoverSecondMonth(), dataSheetColumnIndices.daysSecondMonth());
        final String thirdMonthPlanTurnover = getDataPlanFormula(dataSheetColumnIndices, dataSheetColumnIndices.planAvgTurnoverThirdMonth(), dataSheetColumnIndices.daysThirdMonth());
        final String firstMonthPlanMargin = getDataPlanFormula(dataSheetColumnIndices, dataSheetColumnIndices.planAvgMarginFirstMonth(), dataSheetColumnIndices.daysFirstMonth());
        final String secondMonthPlanMargin = getDataPlanFormula(dataSheetColumnIndices, dataSheetColumnIndices.planAvgMarginSecondMonth(), dataSheetColumnIndices.daysSecondMonth());
        final String thirdMonthPlanMargin = getDataPlanFormula(dataSheetColumnIndices, dataSheetColumnIndices.planAvgMarginThirdMonth(), dataSheetColumnIndices.daysThirdMonth());
        setFormula(firstMonthPlanTurnover, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planTurnoverFirstMonth());
        setFormula(firstMonthPlanMargin, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planMarginFirstMonth());
        setFormula(secondMonthPlanTurnover, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planTurnoverSecondMonth());
        setFormula(secondMonthPlanMargin, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planMarginSecondMonth());
        setFormula(thirdMonthPlanTurnover, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planTurnoverThirdMonth());
        setFormula(thirdMonthPlanMargin, dataSheet, INITIAL_VALUE_ROW_INDEX, endRow, dataSheetColumnIndices.planMarginThirdMonth());
    }

    private Sheet getCategoriesSheet(final Workbook workbook,
                                     final Quarter plannedQuarter,
                                     final Collection<StoreCategorySales> sales) {
        final CategoriesSheetColumnIndices categoriesIndices = columnIndices.categoriesSheet();
        final Sheet categoriesSheet = workbook.createSheet(CATEGORY_SHEET_NAME);
        final Set<String> categories = sales.stream()
                .map(StoreCategorySales::getCategoryName)
                .collect(Collectors.toSet());
        final int endRow = INITIAL_VALUE_ROW_INDEX + categories.size();

        createCells(categoriesSheet, endRow, 6);
        createResultHeaders(categoriesSheet, CATEGORY, categoriesIndices, plannedQuarter);
        populateCategoriesSheetFormulas(categoriesSheet, columnIndices, endRow, categories);

        return categoriesSheet;
    }

    private static void populateCategoriesSheetFormulas(final Sheet categoriesSheet, final ColumnIndices columnIndices,
                                                        final int endRow, final Set<String> categories) {
        final CategoriesSheetColumnIndices categoryIndices = columnIndices.categoriesSheet();
        final DataSheetColumnIndices dataIndices = columnIndices.dataSheet();
        final int dataSheetCategoryColumnIndex = dataIndices.category();
        final int categorySheetCategoryColumnIndex = categoryIndices.category();

        final String firstMonthPlanTurnover = getPlanSumFormula(dataSheetCategoryColumnIndex, dataIndices.planTurnoverFirstMonth(), categorySheetCategoryColumnIndex);
        final String firstMonthPlanMargin = getPlanSumFormula(dataSheetCategoryColumnIndex, dataIndices.planMarginFirstMonth(), categorySheetCategoryColumnIndex);
        final String secondMonthPlanTurnover = getPlanSumFormula(dataSheetCategoryColumnIndex, dataIndices.planTurnoverSecondMonth(), categorySheetCategoryColumnIndex);
        final String secondMonthPlanMargin = getPlanSumFormula(dataSheetCategoryColumnIndex, dataIndices.planMarginSecondMonth(), categorySheetCategoryColumnIndex);
        final String thirdMonthPlanTurnover = getPlanSumFormula(dataSheetCategoryColumnIndex, dataIndices.planTurnoverThirdMonth(), categorySheetCategoryColumnIndex);
        final String thirdMonthPlanMargin = getPlanSumFormula(dataSheetCategoryColumnIndex, dataIndices.planMarginThirdMonth(), categorySheetCategoryColumnIndex);

        int startRow = INITIAL_VALUE_ROW_INDEX;
        for (final String category : categories) {
            categoriesSheet.getRow(startRow++).getCell(0).setCellValue(category);
        }

        setFormula(firstMonthPlanTurnover, categoriesSheet, INITIAL_VALUE_ROW_INDEX, endRow, 1);
        setFormula(firstMonthPlanMargin, categoriesSheet, INITIAL_VALUE_ROW_INDEX, endRow, 2);
        setFormula(secondMonthPlanTurnover, categoriesSheet, INITIAL_VALUE_ROW_INDEX, endRow, 3);
        setFormula(secondMonthPlanMargin, categoriesSheet, INITIAL_VALUE_ROW_INDEX, endRow, 4);
        setFormula(thirdMonthPlanTurnover, categoriesSheet, INITIAL_VALUE_ROW_INDEX, endRow, 5);
        setFormula(thirdMonthPlanMargin, categoriesSheet, INITIAL_VALUE_ROW_INDEX, endRow, 6);

        categoriesSheet.getRow(endRow).getCell(0).setCellValue(TOTAL);
        setFormula(getTotalSumPerRegionFormula(INITIAL_VALUE_ROW_INDEX, endRow - 1), categoriesSheet, endRow, endRow, 1, 6);
    }

    public Sheet createStoresSheet(final Workbook workbook, final Quarter plannedQuarter,
                                   final Collection<StoreCategorySales> sales, final List<RegionOrder> regionOrders) {
        final StoresSheetColumnIndices storesIndices = columnIndices.storesSheet();
        final Sheet storesSheet = workbook.createSheet(STORES_SHEET_NAME);
        final Map<String, Integer> regionOrder = regionOrders.stream()
                .collect(toMap(
                        RegionOrder::name,
                        RegionOrder::sortOrder
                ));

        final Map<String, Set<String>> storesPerRegion = sales.stream()
                .collect(groupingBy(
                        StoreCategorySales::getRegionName,
                        () -> new TreeMap<>(Comparator.comparing(regionName -> regionOrder.getOrDefault(regionName, 0))),
                        mapping(
                                StoreCategorySales::getStoreName,
                                toCollection(TreeSet::new)
                        )
                ));
        final int regionsCount = storesPerRegion.size();
        final int storesCount = storesPerRegion.values().stream()
                .mapToInt(Collection::size)
                .sum();
        final int totalValues = regionsCount + storesCount;
        final int endRow = INITIAL_VALUE_ROW_INDEX + totalValues;

        createCells(storesSheet, endRow, 8);

        createResultHeaders(storesSheet, STORE, storesIndices, plannedQuarter);
        createAdjustedStoresPlanHeaders(storesSheet, storesIndices, plannedQuarter);
        populateStoresData(storesPerRegion, storesSheet, storesIndices);
        populateStoresSheetFormulas(storesSheet, columnIndices, storesPerRegion, endRow);

        return storesSheet;
    }

    private static void createAdjustedStoresPlanHeaders(final Sheet storesSheet,
                                                        final StoresSheetColumnIndices storesSheetColumnIndices,
                                                        final Quarter plannedQuarter) {
        final int adjustedPlanTurnoverColumnIndex = storesSheetColumnIndices.adjustedPlanTurnoverFirstMonth();
        final int adjustedPlanMarginColumnIndex = storesSheetColumnIndices.adjustedPlanMarginFirstMonth();
        storesSheet.getRow(FIRST_ROW_INDEX).getCell(adjustedPlanTurnoverColumnIndex).setCellValue("Скоригований план " + plannedQuarter.firstMonthName());
        storesSheet.getRow(SECOND_ROW_INDEX).getCell(adjustedPlanTurnoverColumnIndex).setCellValue(TURNOVER_COLUMN_NAME);
        storesSheet.getRow(SECOND_ROW_INDEX).getCell(adjustedPlanMarginColumnIndex).setCellValue(MARGIN_COLUMN_NAME);
        storesSheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, adjustedPlanTurnoverColumnIndex, adjustedPlanMarginColumnIndex));
    }

    private static void populateStoresData(final Map<String, Set<String>> storesPerRegion, final Sheet storesSheet,
                                           final StoresSheetColumnIndices storesSheetColumnIndices) {
        final int storeColumnIndex = storesSheetColumnIndices.store();
        int rowIndex = INITIAL_VALUE_ROW_INDEX;
        for (final Map.Entry<String, Set<String>> entry : storesPerRegion.entrySet()) {
            final String regionName = entry.getKey();
            storesSheet.getRow(rowIndex++).getCell(storeColumnIndex).setCellValue(regionName);

            for (final String storeName : entry.getValue()) {
                storesSheet.getRow(rowIndex++).getCell(storeColumnIndex).setCellValue(storeName);
            }
        }
        storesSheet.getRow(rowIndex).getCell(storeColumnIndex).setCellValue(TOTAL);
    }

    private static void populateStoresSheetFormulas(final Sheet storesSheet,
                                                    final ColumnIndices columnIndices,
                                                    final Map<String, Set<String>> storesPerRegion,
                                                    final int endRow) {
        final StoresSheetColumnIndices storesSheetColumnIndices = columnIndices.storesSheet();
        final DataSheetColumnIndices dataSheetColumnIndices = columnIndices.dataSheet();
        final int dataSheetStoreIndex = dataSheetColumnIndices.store();
        final int storeSheetStoreIndex = storesSheetColumnIndices.store();
        final Map<String, RegionRowInfo> regionRowInfo = getRegionRowInfo(storesPerRegion);

        final String planTurnoverFirstMonthFormula = getPlanSumFormula(dataSheetStoreIndex, dataSheetColumnIndices.planTurnoverFirstMonth(), storeSheetStoreIndex);
        final String planMarginFirstMonthFormula = getPlanSumFormula(dataSheetStoreIndex, dataSheetColumnIndices.planMarginFirstMonth(), storeSheetStoreIndex);
        final String planTurnoverSecondMonthFormula = getPlanSumFormula(dataSheetStoreIndex, dataSheetColumnIndices.planTurnoverSecondMonth(), storeSheetStoreIndex);
        final String planMarginSecondMonthFormula = getPlanSumFormula(dataSheetStoreIndex, dataSheetColumnIndices.planMarginSecondMonth(), storeSheetStoreIndex);
        final String planTurnoverThirdMonthFormula = getPlanSumFormula(dataSheetStoreIndex, dataSheetColumnIndices.planTurnoverThirdMonth(), storeSheetStoreIndex);
        final String planMarginThirdMonthFormula = getPlanSumFormula(dataSheetStoreIndex, dataSheetColumnIndices.planMarginThirdMonth(), storeSheetStoreIndex);
        final String adjustedMarginFirstMonthFormula = getAdjustedMarginStoresFormula(storesSheetColumnIndices.planMarginFirstMonth(), storesSheetColumnIndices.planTurnoverFirstMonth(), storesSheetColumnIndices.adjustedPlanTurnoverFirstMonth());

        int rowIndex = INITIAL_VALUE_ROW_INDEX;
        for (int i = rowIndex; i <= endRow; i++) {
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.planTurnoverFirstMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, planTurnoverFirstMonthFormula));
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.planMarginFirstMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, planMarginFirstMonthFormula));
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.planTurnoverSecondMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, planTurnoverSecondMonthFormula));
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.planMarginSecondMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, planMarginSecondMonthFormula));
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.planTurnoverThirdMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, planTurnoverThirdMonthFormula));
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.planMarginThirdMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, planMarginThirdMonthFormula));

            final String adjustedTurnoverPlanFormula = resolveStoresFormula(storesSheet, i, regionRowInfo, Strings.EMPTY);
            if (!Strings.isEmpty(adjustedTurnoverPlanFormula)) {
                storesSheet.getRow(i).getCell(storesSheetColumnIndices.adjustedPlanTurnoverFirstMonth())
                        .setCellFormula(adjustedTurnoverPlanFormula);
            }
            storesSheet.getRow(i).getCell(storesSheetColumnIndices.adjustedPlanMarginFirstMonth())
                    .setCellFormula(resolveStoresFormula(storesSheet, i, regionRowInfo, adjustedMarginFirstMonthFormula));
        }
    }

    private static Map<String, RegionRowInfo> getRegionRowInfo(final Map<String, Set<String>> storesPerRegion) {
        int rowIndex = INITIAL_VALUE_ROW_INDEX;
        final List<RegionRowInfo> regionRowInfo = new ArrayList<>();
        for (final Map.Entry<String, Set<String>> entry : storesPerRegion.entrySet()) {
            final int storesCountPerRegion = entry.getValue().size();
            final RegionRowInfo regionRowData = new RegionRowInfo(
                    entry.getKey(),
                    rowIndex,
                    rowIndex + 1,
                    rowIndex + storesCountPerRegion
            );
            regionRowInfo.add(regionRowData);
            rowIndex = rowIndex + storesCountPerRegion + 1;
        }
        return regionRowInfo.stream()
                .collect(toMap(
                        RegionRowInfo::regionName,
                        Function.identity()
                ));
    }

    private static String resolveStoresFormula(final Sheet sheet, final int currentRowIndex,
                                               final Map<String, RegionRowInfo> regionRowInfo,
                                               final String defaultStoreFormula) {
        final String currentEntity = sheet.getRow(currentRowIndex).getCell(0).getStringCellValue();
        if (TOTAL.equals(currentEntity)) {
            return getOverallSumFormula(new ArrayList<>(regionRowInfo.values()));
        }
        if (regionRowInfo.containsKey(currentEntity)) {
            final RegionRowInfo rowInfo = regionRowInfo.get(currentEntity);
            return getTotalSumPerRegionFormula(rowInfo.regionStoresStartRowIndex(), rowInfo.regionStoresEndRowIndex());
        }
        return defaultStoreFormula;

    }

    private static void createResultHeaders(final Sheet sheet,
                                            final String sheetName,
                                            final ResultSheetColumnIndices columnIndices,
                                            final Quarter plannedQuarter) {
        final Row firstRow = sheet.getRow(FIRST_ROW_INDEX);
        final Row secondRow = sheet.getRow(SECOND_ROW_INDEX);
        final Set<Integer> turnoverColumnIndices = columnIndices.getTurnoverColumnIndices();
        final Set<Integer> marginColumnIndices = columnIndices.getMarginColumnIndices();

        final int categoryOrStoreColumnIndex = columnIndices.getCategoryOrStoreColumnIndex();
        firstRow.getCell(categoryOrStoreColumnIndex).setCellValue(sheetName);
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, categoryOrStoreColumnIndex, categoryOrStoreColumnIndex));

        final int planTurnoverFirstMonthColumnIndex = columnIndices.getPlanTurnoverFirstMonthColumnIndex();
        final int planMarginFirstMonthColumnIndex = columnIndices.getPlanMarginFirstMonthColumnIndex();
        firstRow.getCell(planTurnoverFirstMonthColumnIndex).setCellValue(format("План %s", plannedQuarter.firstMonthName()));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planTurnoverFirstMonthColumnIndex, planMarginFirstMonthColumnIndex));
        final int planTurnoverSecondMonthColumnIndex = columnIndices.getPlanTurnoverSecondMonthColumnIndex();
        final int planMarginSecondMonthColumnIndex = columnIndices.getPlanMarginSecondMonthColumnIndex();
        firstRow.getCell(planTurnoverSecondMonthColumnIndex).setCellValue(format("План %s", plannedQuarter.secondMonth()));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planTurnoverSecondMonthColumnIndex, planMarginSecondMonthColumnIndex));
        final int planTurnoverThirdMonthColumnIndex = columnIndices.getPlanTurnoverThirdMonthColumnIndex();
        final int planMarginThirdMonthColumnIndex = columnIndices.getPlanMarginThirdMonthColumnIndex();
        firstRow.getCell(planTurnoverThirdMonthColumnIndex).setCellValue(format("План %s", plannedQuarter.thirdMonthName()));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, planTurnoverThirdMonthColumnIndex, planMarginThirdMonthColumnIndex));

        turnoverColumnIndices.forEach(i -> secondRow.getCell(i).setCellValue(TURNOVER_COLUMN_NAME));
        marginColumnIndices.forEach(i -> secondRow.getCell(i).setCellValue(MARGIN_COLUMN_NAME));
    }

    private static String resolveFileName(final Quarter plannedQuarter) {
        return String.format(
                "Квартальний план (%s__%s__%s).xlsx",
                plannedQuarter.firstMonthName(),
                plannedQuarter.secondMonth(),
                plannedQuarter.thirdMonthName()
        );
    }
}
