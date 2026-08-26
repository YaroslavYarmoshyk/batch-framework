package com.etake.avgcheckplan.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class AvgCheckBatchJobConfig {
    private final JobRepository jobRepository;

    @Bean
    public Job job(final Step avgCheckPlanStep) {
        return new JobBuilder("turnoverPlanJob", jobRepository)
                .start(avgCheckPlanStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step avgCheckPlanStep(final PlatformTransactionManager transactionManager, final AvgCheckTasklet avgCheckTasklet) {
        return new StepBuilder("avgCheckPlanStep", jobRepository)
                .tasklet(avgCheckTasklet, transactionManager)
                .build();
    }
}
