package com.etake.storeplanadjustment.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {
    private final JobRepository jobRepository;

    @Bean
    public Job job(final Step storePlanAdjustmentStep) {
        return new JobBuilder("storePlanAdjustmentJob", jobRepository)
                .start(storePlanAdjustmentStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step storePlanAdjustmentStep(final PlatformTransactionManager transactionManager,
                                        final StorePlanAdjustmentTasklet storePlanAdjustmentTasklet) {
        return new StepBuilder("storePlanAdjustmentStep", jobRepository)
                .tasklet(storePlanAdjustmentTasklet, transactionManager)
                .build();
    }
}
