package com.etake.shelvesdistribution.config;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShelvesDistributionJob {
    private final JobRepository jobRepository;
    private final ShelvesDistributionStep shelvesDistributionStep;

    @Bean
    public Job job() {
        return new JobBuilder("shelvesDistributionJob", jobRepository)
                .start(shelvesDistributionStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }
}
