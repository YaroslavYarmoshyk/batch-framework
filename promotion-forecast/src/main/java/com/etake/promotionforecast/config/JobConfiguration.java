package com.etake.promotionforecast.config;

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
    public Job job(final Step promotionForecastStep) {
        return new JobBuilder("promotionForecastJob", jobRepository)
                .start(promotionForecastStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

    @Bean
    public Step promotionForecastStep(final PlatformTransactionManager transactionManager, final PromotionForecastTasklet tasklet) {
        return new StepBuilder("promotionForecastStep", jobRepository)
                .tasklet(tasklet, transactionManager)
                .build();
    }
}
