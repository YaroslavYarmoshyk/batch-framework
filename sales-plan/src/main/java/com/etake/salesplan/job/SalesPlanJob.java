package com.etake.salesplan.job;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SalesPlanJob {
    private final JobRepository jobRepository;
    private final SalesPlanStep salesPlanStep;

    @Bean
    public Job job() {
        return new JobBuilder("salesPlanJob", jobRepository)
                .start(salesPlanStep)
                .incrementer(new RunIdIncrementer())
                .build();
    }

}
