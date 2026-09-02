package com.etake.turnoverplan.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class BatchJobConfig {
    private final JobRepository jobRepository;

    @Bean
    public Job job(final Step turnoverPlanStep) {
        return new JobBuilder("turnoverPlanJob", jobRepository)
                .start(turnoverPlanStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step turnoverPlanStep(final PlatformTransactionManager transactionManager, final TurnoverPlanTasklet turnoverPlanTasklet) {
        return new StepBuilder("turnoverPlanStep", jobRepository)
                .tasklet(turnoverPlanTasklet, transactionManager)
                .build();
    }
}
