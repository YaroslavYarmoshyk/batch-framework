package com.etake.shelvesdistribution.config;

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
public class ShelvesDistributionJob {
    private final JobRepository jobRepository;

    @Bean
    public Job job(final Step shelvesDistributionStep) {
        return new JobBuilder("shelvesDistributionJob", jobRepository)
                .start(shelvesDistributionStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step shelvesDistributionStep(final PlatformTransactionManager transactionManager, final ShelvesDistributionTasklet shelvesDistributionTasklet) {
        return new StepBuilder("shelvesDistributionStep", jobRepository)
                .tasklet(shelvesDistributionTasklet, transactionManager)
                .build();
    }
}
