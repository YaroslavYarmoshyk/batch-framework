package com.etake.shelvesdistribution.config;

import com.etake.shelvesdistribution.service.ShelvesDistributionService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShelvesDistributionTasklet implements Tasklet {
    private final ShelvesDistributionService shelvesDistributionService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {
        shelvesDistributionService.generateShelvesDistributionReport();
        return RepeatStatus.FINISHED;
    }
}
