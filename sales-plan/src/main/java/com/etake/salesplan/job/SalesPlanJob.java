package com.etake.salesplan.job;

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
public class SalesPlanJob {
    private final JobRepository jobRepository;

    @Bean
    public Job job(final Step salesPlanStep) {
        return new JobBuilder("salesPlanJob", jobRepository)
                .start(salesPlanStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step salesPlanStep(final PlatformTransactionManager transactionManager, final SalesPlanTasklet salesPlanTasklet) {
        return new StepBuilder("salesPlanStep", jobRepository)
                .tasklet(salesPlanTasklet, transactionManager)
                .build();
    }
}
