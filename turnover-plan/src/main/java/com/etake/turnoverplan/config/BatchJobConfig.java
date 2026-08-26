package com.etake.turnoverplan.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BatchJobConfig {
    private final JobRepository jobRepository;
    private final Step turnoverPlanStep;

    @Bean
    public Job job() {
        return new JobBuilder("turnoverPlanJob", jobRepository)
                .start(turnoverPlanStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }
}
