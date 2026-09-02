package com.etake.salesplan.job;

import com.etake.salesplan.service.StoreCategorySalesService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesPlanTasklet implements Tasklet {
    private final StoreCategorySalesService storeCategorySalesService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {
        storeCategorySalesService.getSales();
        return RepeatStatus.FINISHED;
    }
}
