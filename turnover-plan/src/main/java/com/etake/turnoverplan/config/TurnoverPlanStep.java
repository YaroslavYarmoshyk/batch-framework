package com.etake.turnoverplan.config;

import com.etake.turnoverplan.config.properties.SystemConfigurationProperties;
import com.etake.turnoverplan.service.ExcelService;
import com.etake.turnoverplan.service.StoreCategoryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TurnoverPlanStep implements Step {
    private final ExcelService excelService;
    private final StoreCategoryService storeCategoryService;
    private final SystemConfigurationProperties systemConfigurationProperties;

    @NonNull
    @Override
    public String getName() {
        return "turnoverPlanStep";
    }

    @Override
    public void execute(@NonNull final StepExecution stepExecution) {
        try {
            final Workbook workbook = excelService.getWorkbook(storeCategoryService.getSales());
            excelService.writeWorkbook(workbook, systemConfigurationProperties.output());
            stepExecution.setStatus(BatchStatus.COMPLETED);
            stepExecution.setExitStatus(ExitStatus.COMPLETED);
        } catch (final Exception e) {
            throw new IllegalStateException("Cannot write the workbook", e);
        }
    }
}
