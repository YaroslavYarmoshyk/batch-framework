package com.etake.cyclicaction;

import com.excel.custom.library.annotation.EnableExcelLibrary;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableExcelLibrary
public class CyclicActionApplication {

    static void main(String[] args) {
        SpringApplication.run(CyclicActionApplication.class, args);
    }

    @Bean
    ApplicationRunner runner(JobOperator jobOperator, Job job) {
        return _ -> jobOperator.start(job, new JobParameters());
    }
}
