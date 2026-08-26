package com.etake.cyclicaction.config.reader;

import com.etake.cyclicaction.config.annotations.ActionHistoryReader;
import com.etake.cyclicaction.config.annotations.ActualAvgSalesReader;
import com.etake.cyclicaction.config.mapper.PositionRowMapper;
import com.etake.cyclicaction.config.mapper.SalesPeriodRowMapper;
import com.etake.cyclicaction.config.properties.ExcelManagementProperties;
import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.model.SalesPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.extensions.excel.streaming.StreamingXlsxItemReader;
import org.springframework.batch.extensions.excel.support.rowset.DefaultRowSetFactory;
import org.springframework.batch.extensions.excel.support.rowset.RowSetFactory;
import org.springframework.batch.extensions.excel.support.rowset.StaticColumnNameExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import static com.etake.cyclicaction.util.Constants.ACTION_HISTORY_COLUMN_NAMES;
import static com.etake.cyclicaction.util.Constants.ACTUAL_AVG_SALES_COLUMN_NAMES;


@Configuration
@RequiredArgsConstructor
public class StreamingExcelItemReaderConfig {
    private final ExcelManagementProperties excelProperties;
    private final PositionRowMapper positionRowMapper;
    private final SalesPeriodRowMapper salesPeriodRowMapper;

    @Bean
    @StepScope
    @ActionHistoryReader
    public StreamingXlsxItemReader<Position> actionHistoryReader() {
        final StreamingXlsxItemReader<Position> reader = new StreamingXlsxItemReader<>();
        reader.setResource(new FileSystemResource(excelProperties.getActionHistoryFilePath()));
        reader.setLinesToSkip(excelProperties.getActionHistorySkipLines());
        reader.setRowMapper(positionRowMapper);
        reader.setRowSetFactory(getRowSetFactory(ACTION_HISTORY_COLUMN_NAMES));
        return reader;
    }

    @Bean
    @StepScope
    @ActualAvgSalesReader
    public StreamingXlsxItemReader<SalesPeriod> actualAvgSalesReader() {
        final StreamingXlsxItemReader<SalesPeriod> reader = new StreamingXlsxItemReader<>();
        reader.setResource(new FileSystemResource(excelProperties.getAvgSalesFilePath()));
        reader.setLinesToSkip(excelProperties.getAvgSalesSkipLines());
        reader.setRowMapper(salesPeriodRowMapper);
        reader.setRowSetFactory(getRowSetFactory(ACTUAL_AVG_SALES_COLUMN_NAMES));
        return reader;
    }

    private static RowSetFactory getRowSetFactory(final String[] columnNames) {
        final StaticColumnNameExtractor extractor = new StaticColumnNameExtractor(columnNames);
        final DefaultRowSetFactory rowSetFactory = new DefaultRowSetFactory();
        rowSetFactory.setColumnNameExtractor(extractor);
        return rowSetFactory;
    }
}
