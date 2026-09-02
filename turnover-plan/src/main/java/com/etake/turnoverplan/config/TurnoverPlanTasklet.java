package com.etake.turnoverplan.config;

import com.etake.turnoverplan.config.properties.SystemConfigurationProperties;
import com.etake.turnoverplan.service.ExcelService;
import com.etake.turnoverplan.service.StoreCategoryService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TurnoverPlanTasklet implements Tasklet {
    private final ExcelService excelService;
    private final StoreCategoryService storeCategoryService;
    private final SystemConfigurationProperties systemConfigurationProperties;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {
        final Workbook workbook = excelService.getWorkbook(storeCategoryService.getSales());
        excelService.writeWorkbook(workbook, systemConfigurationProperties.output());
        return RepeatStatus.FINISHED;
    }
}
