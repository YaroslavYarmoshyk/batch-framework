package com.etake.cyclicaction.config.writer;

import com.etake.cyclicaction.config.properties.ExcelManagementProperties;
import com.etake.cyclicaction.dao.CyclicActionState;
import com.etake.cyclicaction.model.Position;
import com.excel.custom.library.service.ExcelFormatService;
import com.excel.custom.library.util.ExcelUtils;
import com.excel.custom.library.util.FormulasUtils;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.etake.cyclicaction.util.Constants.ACTION_AVERAGE_SALES;
import static com.etake.cyclicaction.util.Constants.ACTUAL_AVERAGE_SALES_RESULT;
import static com.etake.cyclicaction.util.Constants.BEFORE_ACTION_AVERAGE_SALES;
import static com.etake.cyclicaction.util.Constants.CARRYOVER;
import static com.etake.cyclicaction.util.Constants.DEFAULT_COEFFICIENT;
import static com.etake.cyclicaction.util.Constants.DEFAULT_DATE_FORMAT;
import static com.etake.cyclicaction.util.Constants.DEMAND_DAYS;
import static com.etake.cyclicaction.util.Constants.DYNAMIC_COEFFICIENT;
import static com.etake.cyclicaction.util.Constants.FORECAST_ACTION_AVERAGE_SALES;
import static com.etake.cyclicaction.util.Constants.FORECAST_SALES;
import static com.etake.cyclicaction.util.Constants.FRACTIONAL_COLUMN_NAMES;
import static com.etake.cyclicaction.util.Constants.MAX_COEFFICIENT;
import static com.etake.cyclicaction.util.CyclicUtil.createPositionSuppliers;
import static com.etake.cyclicaction.util.CyclicUtil.createReportColumnIndexes;
import static com.excel.custom.library.util.ExcelUtils.setCellValue;

@Component
@RequiredArgsConstructor
public class ExcelPoiItemWriter implements ItemStreamWriter<Position> {
    private final ExcelFormatService excelFormatService;
    private final ExcelManagementProperties excelProperties;
    private final CyclicActionState cyclicActionState;

    @Value("${cyclic-action.start-date}")
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate actionStartDate;
    @Value("${cyclic-action.end-date}")
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate actionEndDate;

    private XSSFWorkbook workbook;
    private Sheet sheet;
    private static final Map<String, Integer> indexes = createReportColumnIndexes();
    private static final Map<String, Function<Position, Object>> positionSuppliers = createPositionSuppliers();

    private static final int forecastAvgSalesIndex = indexes.get(FORECAST_ACTION_AVERAGE_SALES);
    private static final int dynamicCoefficientIndex = indexes.get(DYNAMIC_COEFFICIENT);
    private static final int beforeAverageSalesIndex = indexes.get(BEFORE_ACTION_AVERAGE_SALES);
    private static final int actionAverageSalesIndex = indexes.get(ACTION_AVERAGE_SALES);
    private static final int actualAverageSalesIndex = indexes.get(ACTUAL_AVERAGE_SALES_RESULT);
    private static final int forecastSalesIndex = indexes.get(FORECAST_SALES);
    private static final int carryoverIndex = indexes.get(CARRYOVER);

    private int currentRowIndex = 1;

    @Override
    public void open(@NonNull final ExecutionContext executionContext) throws ItemStreamException {
        try {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("result");
            createHeader();
            ExcelUtils.createCells(sheet, cyclicActionState.getCyclicAction().size(), indexes.size());
        } catch (Exception e) {
            throw new ItemStreamException("Failed to initialize ExcelPoiItemWriter", e);
        }
    }

    private void createHeader() {
        final Row header = sheet.createRow(0);
        indexes.forEach((key, value) -> header.createCell(value)
                .setCellValue(key));
        final Workbook workbook = sheet.getWorkbook();
        final CellStyle headerStyle = excelFormatService.getHeaderStyle(workbook);
        final CellStyle headerStyleGreen = excelFormatService.getHeaderStyle(workbook, IndexedColors.DARK_GREEN);
        ExcelUtils.applyCellStyle(sheet, headerStyle, 0, 0, 0, 12);
        ExcelUtils.applyCellStyle(sheet, headerStyleGreen, 0, 13);
    }

    @Override
    public void update(@NonNull final ExecutionContext executionContext) throws ItemStreamException {
        // no-op
    }

    @Override
    public void write(@NonNull final Chunk<? extends Position> chunk) {
        for (final Position position : chunk.getItems()) {
            final Row row = sheet.getRow(currentRowIndex++);
            final int rowNum = row.getRowNum();
            indexes.forEach((key, index) -> setValueToCell(position, row, key, index));
            setFormulas(rowNum);
            setStyle();
        }
    }

    private void setFormulas(final int rowNum) {
        final String beforeAverageSalesCell = FormulasUtils.getRange(rowNum, beforeAverageSalesIndex);
        final String actionAverageSalesCell = FormulasUtils.getRange(rowNum, actionAverageSalesIndex);
        final String actualAverageSalesCell = FormulasUtils.getRange(rowNum, actualAverageSalesIndex);
        final String dynamicCoefficientCell = FormulasUtils.getRange(rowNum, dynamicCoefficientIndex);
        final String forecastAvgSalesCell = FormulasUtils.getRange(rowNum, forecastAvgSalesIndex);
        final String carryoverCell = FormulasUtils.getRange(rowNum, carryoverIndex);

        final String coefficientFormula = FormulasUtils.getCyclicActionCoefficientFormula(
                beforeAverageSalesCell,
                actionAverageSalesCell,
                MAX_COEFFICIENT,
                DEFAULT_COEFFICIENT
        );
        sheet.getRow(rowNum).getCell(dynamicCoefficientIndex).setCellFormula(coefficientFormula);

        final String forecastAvgFormula = FormulasUtils.getCyclicForecastAvgFormula(
                beforeAverageSalesCell,
                actionAverageSalesCell,
                actualAverageSalesCell,
                dynamicCoefficientCell,
                DEFAULT_COEFFICIENT
        );
        sheet.getRow(rowNum).getCell(forecastAvgSalesIndex).setCellFormula(forecastAvgFormula);

        final String cyclicForecastFormula = FormulasUtils.getCyclicForecastFormula(
                forecastAvgSalesCell,
                carryoverCell,
                DEMAND_DAYS
        );
        sheet.getRow(rowNum).getCell(forecastSalesIndex).setCellFormula(cyclicForecastFormula);
    }

    private void setStyle() {
        sheet.createFreezePane(0, 1);
        final CellStyle cellStyle = createFractionalDataStyle(sheet);
        for (final String name : FRACTIONAL_COLUMN_NAMES) {
            final Integer index = indexes.get(name);
            ExcelUtils.applyCellStyle(sheet, cellStyle, 1, index, cyclicActionState.getCyclicAction().size(), index);
        }
    }

    private static CellStyle createFractionalDataStyle(final Sheet sheet) {
        final Workbook workbook = sheet.getWorkbook();
        final CellStyle cellStyle = workbook.createCellStyle();
        final DataFormat format = workbook.createDataFormat();
        cellStyle.setDataFormat(format.getFormat("#,##0.00"));
        return cellStyle;
    }

    private static void setValueToCell(final Position position,
                                       final Row row,
                                       final String key,
                                       final Integer index) {
        final Function<Position, Object> func = positionSuppliers.get(key);
        if (Objects.nonNull(func) && Objects.nonNull(func.apply(position))) {
            setCellValue(row.getCell(index), func.apply(position));
        }
    }

    @Override
    public void close() throws ItemStreamException {
        try {
            try (FileOutputStream outputStream = new FileOutputStream(getFilePath())) {
                workbook.write(outputStream);
            }
            workbook.close();
        } catch (Exception e) {
            throw new ItemStreamException("Failed to write to Excel file", e);
        }
    }

    private String getFilePath() {
        final File directory = excelProperties.getCyclicActionOutputDirectory();
        directory.mkdirs();
        final String fileName = String.format(
                "Циклічна акція_%s-%s.xlsx",
                actionStartDate.format(DEFAULT_DATE_FORMAT),
                actionEndDate.format(DEFAULT_DATE_FORMAT)
        );
        return new File(directory, fileName).getAbsolutePath();
    }
}
