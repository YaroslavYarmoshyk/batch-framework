package com.etake.cyclicforecast.config;

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
public class JobConfiguration {
    private final JobRepository jobRepository;

    @Bean
    public Job job(final Step cyclicForecastStep) {
        return new JobBuilder("cyclicForecastJob", jobRepository)
                .start(cyclicForecastStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step cyclicForecastStep(final PlatformTransactionManager transactionManager, final CyclicForecastTasklet tasklet) {
        return new StepBuilder("cyclicForecastStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
