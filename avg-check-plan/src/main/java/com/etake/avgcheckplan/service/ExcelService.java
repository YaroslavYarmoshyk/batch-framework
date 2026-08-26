package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.Column;
import com.etake.avgcheckplan.config.SystemConfigurationProperties;
import com.etake.avgcheckplan.model.ForecastPosition;
import com.etake.avgcheckplan.model.SeasonalPlanPosition;
import com.etake.avgcheckplan.utils.ResourcePaths;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.etake.avgcheckplan.Column.*;
import static com.etake.avgcheckplan.utils.Constants.RESULT_SHEET;

@Service
@RequiredArgsConstructor
public class ExcelService {
    private static final String DEFAULT_NUMBER_FORMAT = "0.0";
    private static final String DEFAULT_PERCENTAGE_FORMAT = "0.00%";

    private final SystemConfigurationProperties systemConfigurationProperties;

    public void writeWorkbook(final List<ForecastPosition> positions) throws Exception {
        final Workbook workbook = getWorkbook(positions);
        final FileOutputStream outputStream = new FileOutputStream(resolveOutputFile());
        workbook.write(outputStream);
        workbook.close();
    }

    public void writeSeasonalWorkbook(final List<SeasonalPlanPosition> positions) throws Exception {
        final Workbook workbook = getSeasonalWorkbook(positions);
        final FileOutputStream outputStream = new FileOutputStream(resolveOutputFile());
        workbook.write(outputStream);
        workbook.close();
    }

    private File resolveOutputFile() {
        final File outputFile = ResourcePaths.resolve(systemConfigurationProperties.output());
        final File parent = outputFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return outputFile;
    }

    private Workbook getSeasonalWorkbook(final List<SeasonalPlanPosition> positions) {
        final Workbook workbook = new XSSFWorkbook();
        final Sheet sheet = workbook.createSheet(RESULT_SHEET);

        final List<String> headers = List.of("Регіон", "Магазин", "Схожий магазин", "Базовий СЧ",
                "K1", "K2", "Коефіцієнт", "План СЧ", "Стабільна сезонність", "Однорічний коефіцієнт");
        final Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }

        final CellStyle numberCellStyle = workbook.createCellStyle();
        numberCellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(DEFAULT_NUMBER_FORMAT));
        final CellStyle percentageCellStyle = workbook.createCellStyle();
        percentageCellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(DEFAULT_PERCENTAGE_FORMAT));

        for (int i = 0; i < positions.size(); i++) {
            final Row row = sheet.createRow(i + 1);
            final SeasonalPlanPosition position = positions.get(i);
            row.createCell(0).setCellValue(position.region());
            row.createCell(1).setCellValue(position.store());
            if (Objects.nonNull(position.similarStore())) {
                row.createCell(2).setCellValue(position.similarStore());
            }
            setValue(row.createCell(3), position.baseAvgCheck(), numberCellStyle);
            setValue(row.createCell(4), position.k1(), percentageCellStyle);
            setValue(row.createCell(5), position.k2(), percentageCellStyle);
            setValue(row.createCell(6), position.appliedCoefficient(), percentageCellStyle);
            setValue(row.createCell(7), position.plannedAvgCheck(), numberCellStyle);
            row.createCell(8).setCellValue(position.stableSeasonality());
            row.createCell(9).setCellValue(position.singleYearCoefficient());
        }

        return workbook;
    }

    private Workbook getWorkbook(final List<ForecastPosition> positions) {
        final Workbook workbook = new XSSFWorkbook();
        final Sheet sheet = workbook.createSheet(RESULT_SHEET);

        final Row headerRow = sheet.createRow(0);
        final Map<Column, Integer> columns = systemConfigurationProperties.columns();

        final Integer regionIndex = columns.get(REGION);
        final Integer storeIndex = columns.get(STORE);
        final Integer similarStoreIndex = columns.get(SIMILAR_STORE);
        final Integer prevAvgCheckToDateIndex = columns.get(PREV_AVG_CHECK_TO_DATE);
        final Integer prevAvgCheckLastDayIndex = columns.get(PREV_AVG_CHECK_LAST_DAY);
        final Integer dynamicIndex = columns.get(DYNAMIC);
        final Integer avgCheckToDateIndex = columns.get(AVG_CHECK_TO_DATE);
        final Integer forecastIndex = columns.get(FORECAST);
        final Integer currentAvgCheckIndex = columns.get(CURRENT_AVG_CHECK);
        final Integer fulfilmentIndex = columns.get(FULFILMENT);
        final Integer adjustedForecastIndex = columns.get(ADJUSTED_FORECAST);
        final Integer adjustedFulfilmentIndex = columns.get(ADJUSTED_FULFILMENT);

        headerRow.createCell(regionIndex).setCellValue(REGION.getName());
        headerRow.createCell(storeIndex).setCellValue(STORE.getName());
        headerRow.createCell(similarStoreIndex).setCellValue(SIMILAR_STORE.getName());
        headerRow.createCell(prevAvgCheckToDateIndex).setCellValue(PREV_AVG_CHECK_TO_DATE.getName());
        headerRow.createCell(prevAvgCheckLastDayIndex).setCellValue(PREV_AVG_CHECK_LAST_DAY.getName());
        headerRow.createCell(dynamicIndex).setCellValue(DYNAMIC.getName());
        headerRow.createCell(avgCheckToDateIndex).setCellValue(AVG_CHECK_TO_DATE.getName());
        headerRow.createCell(forecastIndex).setCellValue(FORECAST.getName());
        headerRow.createCell(currentAvgCheckIndex).setCellValue(CURRENT_AVG_CHECK.getName());
        headerRow.createCell(fulfilmentIndex).setCellValue(FULFILMENT.getName());
        headerRow.createCell(adjustedForecastIndex).setCellValue(ADJUSTED_FORECAST.getName());
        headerRow.createCell(adjustedFulfilmentIndex).setCellValue(ADJUSTED_FULFILMENT.getName());

        final CellStyle numberCellStyle = workbook.createCellStyle();
        numberCellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(DEFAULT_NUMBER_FORMAT));
        final CellStyle percentageCellStyle = workbook.createCellStyle();
        percentageCellStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(DEFAULT_PERCENTAGE_FORMAT));

        for (int i = 0; i < positions.size(); i++) {
            final Row row = sheet.createRow(i + 1);
            final ForecastPosition position = positions.get(i);
            row.createCell(regionIndex).setCellValue(position.region());
            row.createCell(storeIndex).setCellValue(position.store());
            row.createCell(similarStoreIndex).setCellValue(position.similarStore());
            setValue(row.createCell(prevAvgCheckToDateIndex), position.prevYearAvgCheckToDate(), numberCellStyle);
            setValue(row.createCell(prevAvgCheckLastDayIndex), position.prevYearAvgCheckLastDay(), numberCellStyle);
            setValue(row.createCell(dynamicIndex), position.dynamic(), percentageCellStyle);
            setValue(row.createCell(avgCheckToDateIndex), position.currYearAvgCheckToDate(), numberCellStyle);
            setValue(row.createCell(forecastIndex), position.forecastedAvgCheckLastDay(), numberCellStyle);
            setValue(row.createCell(currentAvgCheckIndex), position.currentAvgCheckLastDay(), numberCellStyle);
            setValue(row.createCell(fulfilmentIndex), position.fulfilment(), percentageCellStyle);
            setValue(row.createCell(adjustedForecastIndex), position.adjustedForecastedAvgCheckLastDay(), numberCellStyle);
            setValue(row.createCell(adjustedFulfilmentIndex), position.adjustedFulfilment(), percentageCellStyle);
        }

        return workbook;
    }

    private static void setValue(final Cell cell, final BigDecimal bigDecimal, final CellStyle style) {
        if (Objects.nonNull(bigDecimal)) {
            cell.setCellValue(bigDecimal.doubleValue());
            cell.setCellStyle(style);
        }
    }
}
