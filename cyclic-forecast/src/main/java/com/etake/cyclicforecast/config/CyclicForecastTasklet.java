package com.etake.cyclicforecast.config;

import com.etake.cyclicforecast.model.ForecastResult;
import com.etake.cyclicforecast.model.ForecastSummary;
import com.etake.cyclicforecast.model.PlanningScopeRow;
import com.etake.cyclicforecast.model.ReportRow;
import com.etake.cyclicforecast.service.CascadeResolutionService;
import com.etake.cyclicforecast.service.PlanningScopeService;
import com.etake.cyclicforecast.service.ReportRowMapper;
import com.etake.cyclicforecast.service.SummaryReportService;
import com.etake.cyclicforecast.writer.ExcelReportService;
import com.etake.cyclicforecast.writer.GoogleSheetsUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CyclicForecastTasklet implements Tasklet {
    private final PlanningScopeService planningScopeService;
    private final CascadeResolutionService cascadeResolutionService;
    private final ReportRowMapper reportRowMapper;
    private final SummaryReportService summaryReportService;
    private final ExcelReportService excelReportService;
    private final GoogleSheetsUploadService googleSheetsUploadService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {
        final List<PlanningScopeRow> scope = planningScopeService.loadPlanningScope();
        log.info("Loaded {} planning-scope rows", scope.size());

        final List<ForecastResult> results = scope.stream()
                .map(cascadeResolutionService::resolve)
                .toList();

        final ForecastSummary summary = summaryReportService.summarize(results);
        final List<ReportRow> reportRows = reportRowMapper.map(results);

        final File output = excelReportService.writeWorkbook(reportRows, summary);
        log.info("Wrote forecast report to {}", output.getAbsolutePath());

        googleSheetsUploadService.upload(reportRows);

        return RepeatStatus.FINISHED;
    }
}
