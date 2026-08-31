package com.etake.storeplanadjustment.config;

import com.etake.storeplanadjustment.model.AdjustedPlanReportRow;
import com.etake.storeplanadjustment.service.PlanAdjustmentService;
import com.etake.storeplanadjustment.service.ReportExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StorePlanAdjustmentTasklet implements Tasklet {
    private final PlanAdjustmentService planAdjustmentService;
    private final ReportExcelService reportExcelService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution,
                                @NonNull final ChunkContext chunkContext) throws Exception {

        final List<AdjustedPlanReportRow> reportRows = planAdjustmentService.adjustPlans();

        reportExcelService.writeWorkbook(reportRows);

        return RepeatStatus.FINISHED;
    }
}
