package com.etake.salesplan.service;

import com.etake.salesplan.config.YearMonthProperties;
import com.etake.salesplan.model.CategoryAssignment;
import com.etake.salesplan.model.DailySales;
import com.etake.salesplan.model.RegionOrder;
import com.etake.salesplan.model.Sales;
import com.etake.salesplan.repository.CategoriesAssignmentRepository;
import com.etake.salesplan.repository.RegionRepository;
import com.etake.salesplan.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static com.etake.salesplan.constants.ExcelConstants.*;
import static com.etake.salesplan.utils.ExcelFormulaUtils.*;
import static com.etake.salesplan.utils.ExcelUtils.*;
import static java.util.Comparator.comparingInt;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.*;

@Service
@RequiredArgsConstructor
public class ExcelService {
    private final RegionRepository regionRepository;
    private final CategoriesAssignmentRepository categoriesAssignmentRepository;
    private final YearMonthProperties yearMonthProperties;
    @Value("${system-configurations.output}")
    private String output;

    private record CellStyles(CellStyle lightGreyHeaderCellStyle, CellStyle lightGreenHeaderCellStyle,
                              CellStyle valuesCellStyle) {
    }

    public void generateReport(List<DailySales> sales) throws IOException {
        final File directory = ResourcePaths.resolve(output);
        directory.mkdirs();
        final String fileName = String.format("План %s.xlsx", getYearMonthPart(yearMonthProperties.year(), yearMonthProperties.month()));
        try (Workbook workbook = new XSSFWorkbook(); FileOutputStream fileOutputStream = new FileOutputStream(new File(directory, fileName))) {
            List<LocalDate> dates = extractDates(sales);

            Sheet dataSheet = createDataSheet(workbook, sales);
            Sheet storesSheet = createStoresSheet(workbook, sales, dates);
            Sheet categoriesSheet = createCategoriesSheet(workbook, dates);

            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            formulaEvaluator.evaluateAll();

            groupColumns(storesSheet, 1, dates.size());
            groupColumns(categoriesSheet, 1, dates.size());
            groupColumns(categoriesSheet, dates.size() + 2, (2 * dates.size()) + 1);

            autosizeColumns(dataSheet);
            autosizeColumns(storesSheet);
            autosizeColumns(categoriesSheet);

            workbook.write(fileOutputStream);
        }
    }

    private Sheet createDataSheet(Workbook workbook, List<DailySales> sales) {
        Sheet sheet = workbook.createSheet("data");

        int size = sales.stream().mapToInt(s -> s.sales().size()).sum();

        createCells(sheet, INITIAL_VALUE_ROW_INDEX + size, MAX_DATA_SHEET_COLUMN_INDEX);

        createDataSheetHeaders(workbook, sheet);

        fillDataSheet(workbook, sheet, sales);

        return sheet;
    }

    private void createDataSheetHeaders(Workbook workbook, Sheet sheet) {
        Row firstRow = sheet.getRow(FIRST_ROW_INDEX);
        Row secondRow = sheet.getRow(SECOND_ROW_INDEX);

        firstRow.getCell(0).setCellValue(STORE);
        firstRow.getCell(1).setCellValue(SIMILAR_STORE);
        firstRow.getCell(2).setCellValue(CATEGORY);
        firstRow.getCell(3).setCellValue(DATE);

        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, 0, 0));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, 1, 1));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, 2, 2));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, SECOND_ROW_INDEX, 3, 3));

        firstRow.getCell(4).setCellValue("План " + getYearMonthPart(yearMonthProperties.year(), yearMonthProperties.month()));
        sheet.addMergedRegion(new CellRangeAddress(FIRST_ROW_INDEX, FIRST_ROW_INDEX, 4, 5));

        secondRow.getCell(4).setCellValue(TURNOVER);
        secondRow.getCell(5).setCellValue(MARGIN);

        CellStyle greyHeaderCellStyle = getBoldCellStyle(workbook, GREY_COLOR);

        applyCellStyle(sheet, greyHeaderCellStyle, 0, 0, 1, 5);

        sheet.createFreezePane(0, INITIAL_VALUE_ROW_INDEX);
    }

    private void fillDataSheet(Workbook workbook, Sheet sheet, List<DailySales> dailySales) {
        CreationHelper creationHelper = workbook.getCreationHelper();
        CellStyle dateStyle = workbook.createCellStyle();
        dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("dd.MM.yyyy"));
        int rowIndex = INITIAL_VALUE_ROW_INDEX;
        for (DailySales sale : dailySales) {
            for (Map.Entry<LocalDate, Sales> entry : sale.sales().entrySet()) {
                Row row = sheet.getRow(rowIndex++);
                row.getCell(0).setCellValue(sale.store());
                row.getCell(1).setCellValue(sale.similarStore());
                row.getCell(2).setCellValue(sale.category());
                Cell dateCell = row.getCell(3);
                dateCell.setCellValue(entry.getKey());
                dateCell.setCellStyle(dateStyle);
                row.getCell(4).setCellValue(entry.getValue().turnover().doubleValue());
                row.getCell(5).setCellValue(entry.getValue().margin().doubleValue());
            }
        }
    }

    private Sheet createStoresSheet(Workbook workbook, List<DailySales> sales, List<LocalDate> dates) {
        Map<String, RegionOrder> regionsById = regionRepository.findAllRegionOrders().stream().collect(toMap(RegionOrder::id, identity()));
        Map<String, Set<String>> storesByRegion = sales.stream().sorted(comparingInt(s -> regionsById.get(s.regionId()).sortOrder())).collect(Collectors.groupingBy(s -> regionsById.get(s.regionId()).name(), LinkedHashMap::new, Collectors.mapping(DailySales::store, Collectors.toCollection(TreeSet::new))));
        int regions = storesByRegion.size();
        int stores = storesByRegion.values().stream().mapToInt(Collection::size).sum();
        int rowsNumber = INITIAL_VALUE_ROW_INDEX + regions + stores + 1;
        int numberOfDates = dates.size();
        int columnsNumber = numberOfDates + 1;

        Sheet sheet = workbook.createSheet("План по магазинам");

        createCells(sheet, rowsNumber, columnsNumber);

        createStoreSheetHeaders(workbook, sheet, dates);

        fillStoresSheet(workbook, sheet, storesByRegion, numberOfDates);

        sheet.createFreezePane(1, 1);

        return sheet;
    }

    private void createStoreSheetHeaders(Workbook workbook, Sheet sheet, List<LocalDate> dates) {
        CellStyle greyHeaderCellStyle = getBoldCellStyle(workbook, GREY_COLOR);
        CellStyle dateHeaderCellStyle = getBoldCellStyle(workbook, GREY_COLOR);
        dateHeaderCellStyle.setDataFormat(workbook.createDataFormat().getFormat("d.M"));
        Row row = sheet.getRow(FIRST_ROW_INDEX);
        for (int i = 0; i <= dates.size(); i++) {
            Cell cell = row.getCell(i);
            if (i == 0) {
                cell.setCellValue(STORE);
                cell.setCellStyle(greyHeaderCellStyle);
                continue;
            }
            cell.setCellValue(dates.get(i - 1));
            cell.setCellStyle(dateHeaderCellStyle);
        }
        int lastColumnIndex = dates.size() + 1;
        Cell totalCell = row.getCell(lastColumnIndex);
        totalCell.setCellValue(TURNOVER_PLAN);
        totalCell.setCellStyle(greyHeaderCellStyle);
    }

    private void fillStoresSheet(Workbook workbook, Sheet sheet, Map<String, Set<String>> storesByRegion, int datesSize) {
        int rowIndex = 1;
        CellStyles cellStyles = createCellStyles(workbook);

        List<Integer> regionRows = new ArrayList<>();
        int lastColumn = datesSize + 1;
        for (Map.Entry<String, Set<String>> entry : storesByRegion.entrySet()) {
            Row regionRow = sheet.getRow(rowIndex++);
            regionRows.add(rowIndex);
            String region = entry.getKey();
            for (int i = 0; i <= lastColumn; i++) {
                Cell cell = regionRow.getCell(i);
                cell.setCellStyle(cellStyles.lightGreyHeaderCellStyle());
                if (i == 0) {
                    cell.setCellValue(region);
                } else {
                    int startRowIndex = regionRow.getRowNum() + 1;
                    int endRowIndex = startRowIndex + storesByRegion.get(region).size();
                    cell.setCellFormula(getTotalSumPerParentFormula(startRowIndex, endRowIndex));
                }
            }
            for (String store : entry.getValue()) {
                Row storeRow = sheet.getRow(rowIndex++);
                for (int i = 0; i <= lastColumn; i++) {
                    Cell cell = storeRow.getCell(i);
                    cell.setCellStyle(cellStyles.valuesCellStyle());
                    if (i == 0) {
                        cell.setCellValue(store);
                    } else if (i == lastColumn) {
                        String from = new CellReference(storeRow.getRowNum(), 1).formatAsString();
                        String to = new CellReference(storeRow.getRowNum(), lastColumn - 1).formatAsString();
                        cell.setCellFormula("SUM(" + from + ":" + to + ")");
                        cell.setCellStyle(cellStyles.lightGreenHeaderCellStyle());
                    } else {
                        cell.setCellFormula(getSumPerDateFormula(5, 1));
                    }
                }
            }
        }
        Row totalRow = sheet.createRow(rowIndex);
        String totalSumFormula = getTotalSumFormula(regionRows);
        for (int i = 0; i <= lastColumn; i++) {
            Cell cell = totalRow.createCell(i);
            cell.setCellStyle(cellStyles.lightGreyHeaderCellStyle());
            if (i == 0) {
                cell.setCellValue("Разом");
            } else {
                cell.setCellFormula(totalSumFormula);
            }
        }
    }

    private Sheet createCategoriesSheet(Workbook workbook, List<LocalDate> dates) {
        Map<String, List<String>> categoriesByManager = categoriesAssignmentRepository.findCategoryAssignmentsByManagers().stream().collect(groupingBy(CategoryAssignment::manager, LinkedHashMap::new, mapping(CategoryAssignment::category, toList())));
        Sheet sheet = workbook.createSheet("План по категоріям");
        int rowsNumber = Math.toIntExact(INITIAL_VALUE_ROW_INDEX + categoriesByManager.size() + categoriesByManager.values().stream().mapToLong(Collection::size).sum() + 1);
        int numberOfDates = dates.size();
        int columnsNumber = numberOfDates + 1;

        createCells(sheet, rowsNumber, 2 * columnsNumber);

        createCategoriesSheetHeaders(workbook, sheet, dates);

        fillCategoriesSheet(workbook, sheet, categoriesByManager, numberOfDates);

        sheet.createFreezePane(1, 1);

        return sheet;
    }

    private void createCategoriesSheetHeaders(Workbook workbook, Sheet sheet, List<LocalDate> dates) {
        CellStyle greyHeaderCellStyle = getBoldCellStyle(workbook, GREY_COLOR);
        CellStyle dateHeaderCellStyle = getBoldCellStyle(workbook, GREY_COLOR);
        dateHeaderCellStyle.setDataFormat(workbook.createDataFormat().getFormat("d.M"));
        int lastColumnIndex = dates.size() + 1;
        Row row = sheet.getRow(FIRST_ROW_INDEX);
        for (int i = 0; i <= dates.size(); i++) {
            Cell turnoverCell = row.getCell(i);
            Cell marginCell = row.getCell(i + lastColumnIndex);
            if (i == 0) {
                turnoverCell.setCellValue(CATEGORY);
                turnoverCell.setCellStyle(greyHeaderCellStyle);
                continue;
            }
            turnoverCell.setCellValue(dates.get(i - 1));
            turnoverCell.setCellStyle(dateHeaderCellStyle);
            marginCell.setCellValue(dates.get(i - 1));
            marginCell.setCellStyle(dateHeaderCellStyle);
        }
        Cell turnoverTotalCell = row.getCell(lastColumnIndex);
        Cell marginTotalCell = row.getCell(2 * lastColumnIndex);
        turnoverTotalCell.setCellValue(TURNOVER_PLAN);
        turnoverTotalCell.setCellStyle(greyHeaderCellStyle);
        marginTotalCell.setCellValue(MARGIN_PLAN);
        marginTotalCell.setCellStyle(greyHeaderCellStyle);
    }

    private void fillCategoriesSheet(Workbook workbook, Sheet sheet, Map<String, List<String>> categoriesByManager, int datesSize) {
        CellStyles cellStyles = createCellStyles(workbook);
        int rowIndex = 1;
        List<Integer> categoryManagerRows = new ArrayList<>();
        int lastColumn = datesSize + 1;
        for (Map.Entry<String, List<String>> entry : categoriesByManager.entrySet()) {
            Row categoryManagerRow = sheet.getRow(rowIndex++);
            categoryManagerRows.add(rowIndex);
            String manager = entry.getKey();
            for (int i = 0; i <= lastColumn; i++) {
                Cell turnoverCell = categoryManagerRow.getCell(i);
                Cell marginCell = categoryManagerRow.getCell(i + lastColumn);
                turnoverCell.setCellStyle(cellStyles.lightGreyHeaderCellStyle());
                marginCell.setCellStyle(cellStyles.lightGreyHeaderCellStyle());
                if (i == 0) {
                    turnoverCell.setCellValue(manager);
                } else {
                    int startRowIndex = categoryManagerRow.getRowNum() + 1;
                    int endRowIndex = startRowIndex + categoriesByManager.get(manager).size();
                    turnoverCell.setCellFormula(getTotalSumPerParentFormula(startRowIndex, endRowIndex));
                    marginCell.setCellFormula(getTotalSumPerParentFormula(startRowIndex, endRowIndex));
                }
            }
            for (String category : entry.getValue()) {
                Row categoryRow = sheet.getRow(rowIndex++);
                for (int i = 0; i <= lastColumn; i++) {
                    Cell turnoverCell = categoryRow.getCell(i);
                    Cell marginCell = categoryRow.getCell(i + lastColumn);
                    turnoverCell.setCellStyle(cellStyles.valuesCellStyle());
                    marginCell.setCellStyle(cellStyles.valuesCellStyle());
                    if (i == 0) {
                        turnoverCell.setCellValue(category);
                    } else if (i == lastColumn) {
                        String fromTurnover = new CellReference(categoryRow.getRowNum(), 1).formatAsString();
                        String toTurnover = new CellReference(categoryRow.getRowNum(), lastColumn - 1).formatAsString();
                        turnoverCell.setCellFormula("SUM(" + fromTurnover + ":" + toTurnover + ")");
                        turnoverCell.setCellStyle(cellStyles.lightGreenHeaderCellStyle());
                        String fromMargin = new CellReference(categoryRow.getRowNum(), lastColumn + 1).formatAsString();
                        String toMargin = new CellReference(categoryRow.getRowNum(), 2 * lastColumn - 1).formatAsString();
                        marginCell.setCellFormula("SUM(" + fromMargin + ":" + toMargin + ")");
                        marginCell.setCellStyle(cellStyles.lightGreenHeaderCellStyle());
                    } else {
                        turnoverCell.setCellFormula(getSumPerDateFormula(5, 3));
                        marginCell.setCellFormula(getSumPerDateFormula(6, 3));
                    }
                }
            }
        }
        Row totalRow = sheet.createRow(rowIndex);
        String totalSumFormula = getTotalSumFormula(categoryManagerRows);
        for (int i = 0; i <= lastColumn; i++) {
            Cell turnoverCell = totalRow.createCell(i);
            Cell marginCell = totalRow.createCell(i + lastColumn);
            turnoverCell.setCellStyle(cellStyles.lightGreyHeaderCellStyle());
            marginCell.setCellStyle(cellStyles.lightGreyHeaderCellStyle());
            if (i == 0) {
                turnoverCell.setCellValue("Разом");
            } else {
                turnoverCell.setCellFormula(totalSumFormula);
                marginCell.setCellFormula(totalSumFormula);
            }
        }
    }

    private static List<LocalDate> extractDates(List<DailySales> sales) {
        return sales.stream().flatMap(sale -> sale.sales().keySet().stream()).distinct().sorted().toList();
    }

    private static String getYearMonthPart(Integer year, Integer month) {
        return String.format("%s - %s", getMonthPart(month), Math.abs(year % 100));
    }

    private static String getMonthPart(final Integer month) {
        return switch (month) {
            case 1 -> "Січ";
            case 2 -> "Лют";
            case 3 -> "Бер";
            case 4 -> "Кві";
            case 5 -> "Тра";
            case 6 -> "Чер";
            case 7 -> "Лип";
            case 8 -> "Сер";
            case 9 -> "Вер";
            case 10 -> "Жов";
            case 11 -> "Лис";
            case 12 -> "Гру";
            default -> throw new IllegalStateException("Cannot resolve month name");
        };
    }


    private static CellStyles createCellStyles(Workbook workbook) {
        CellStyle lightGreyHeaderCellStyle = getBoldCellStyleWithoutAlignment(workbook, LIGHT_GREY_COLOR);
        CellStyle lightGreenHeaderCellStyle = getBoldCellStyleWithoutAlignment(workbook, LIGHT_GREEN_COLOR, IndexedColors.AUTOMATIC);
        CellStyle valuesCellStyle = getDefaultCellStyle(workbook);
        short valuesDataFormat = workbook.createDataFormat().getFormat("# ### ##0");
        lightGreyHeaderCellStyle.setDataFormat(valuesDataFormat);
        valuesCellStyle.setDataFormat(valuesDataFormat);
        lightGreenHeaderCellStyle.setDataFormat(valuesDataFormat);

        return new CellStyles(lightGreyHeaderCellStyle, lightGreenHeaderCellStyle, valuesCellStyle);
    }

    private static CellStyle getBoldCellStyle(final Workbook workbook, final Color backgroundColor) {
        final CellStyle cellStyle = getBoldCellStyleWithoutAlignment(workbook, backgroundColor);
        cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        cellStyle.setAlignment(HorizontalAlignment.CENTER);
        return cellStyle;
    }

    public static CellStyle getBoldCellStyleWithoutAlignment(final Workbook workbook, final Color backgroundColor) {
        return getBoldCellStyleWithoutAlignment(workbook, backgroundColor, IndexedColors.WHITE);
    }

    public static CellStyle getBoldCellStyleWithoutAlignment(final Workbook workbook, final Color backgroundColor, final IndexedColors colors) {
        final CellStyle cellStyle = getDefaultCellStyle(workbook);
        cellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        cellStyle.setFillForegroundColor(backgroundColor);
        cellStyle.setFont(getFontByColor(workbook, colors));
        cellStyle.setWrapText(true);
        return cellStyle;
    }

    public static CellStyle getDefaultCellStyle(final Workbook workbook) {
        final CellStyle cellStyle = workbook.createCellStyle();
        cellStyle.setBorderTop(BorderStyle.THIN);
        cellStyle.setBorderBottom(BorderStyle.THIN);
        cellStyle.setBorderLeft(BorderStyle.THIN);
        cellStyle.setBorderRight(BorderStyle.THIN);
        return cellStyle;
    }

    private static Font getFontByColor(final Workbook workbook, final IndexedColors color) {
        final Font font = workbook.createFont();
        font.setFontName(DEFAULT_FONT);
        font.setColor(color.getIndex());
        font.setBold(true);
        return font;
    }
}