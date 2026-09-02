package com.etake.cyclicaction.config;

import com.etake.cyclicaction.config.annotations.ActionHistoryReader;
import com.etake.cyclicaction.config.annotations.ActualAvgSalesReader;
import com.etake.cyclicaction.config.listener.JobDiagnosticsListener;
import com.etake.cyclicaction.config.listener.StepDiagnosticsListener;
import com.etake.cyclicaction.config.processor.ActionHistoryItemProcessor;
import com.etake.cyclicaction.config.processor.ActualAvgSalesProcessor;
import com.etake.cyclicaction.config.processor.CyclicActionItemProcessor;
import com.etake.cyclicaction.config.writer.ExcelPoiItemWriter;
import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.model.SalesPeriod;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class JobConfiguration {
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ItemStreamReader<Position> actionHistoryReader;
    private final ItemStreamReader<SalesPeriod> actualAvgSalesReader;
    private final ItemReader<Position> cyclicActionListReader;
    private final ItemStreamWriter<Position> actionHistoryWriter;
    private final ItemStreamWriter<SalesPeriod> actualAvgSalesWriter;
    private final ExcelPoiItemWriter cyclicActionItemWriter;
    private final CyclicActionItemProcessor cyclicActionItemProcessor;
    private final ActionHistoryItemProcessor actionHistoryItemProcessor;
    private final ActualAvgSalesProcessor actualAvgSalesProcessor;
    private final JobDiagnosticsListener jobDiagnosticsListener;
    private final StepDiagnosticsListener stepDiagnosticsListener;

    public JobConfiguration(final JobRepository jobRepository,
                            final PlatformTransactionManager transactionManager,
                            @ActionHistoryReader final ItemStreamReader<Position> actionHistoryReader,
                            @ActualAvgSalesReader final ItemStreamReader<SalesPeriod> actualAvgSalesReader,
                            final ItemReader<Position> cyclicActionListReader,
                            final ItemStreamWriter<Position> actionHistoryWriter,
                            final ItemStreamWriter<SalesPeriod> actualAvgSalesWriter,
                            final ExcelPoiItemWriter cyclicActionItemWriter,
                            final CyclicActionItemProcessor cyclicActionItemProcessor,
                            final ActionHistoryItemProcessor actionHistoryItemProcessor,
                            final ActualAvgSalesProcessor actualAvgSalesProcessor,
                            final JobDiagnosticsListener jobDiagnosticsListener,
                            final StepDiagnosticsListener stepDiagnosticsListener) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.actionHistoryReader = actionHistoryReader;
        this.actualAvgSalesReader = actualAvgSalesReader;
        this.cyclicActionListReader = cyclicActionListReader;
        this.actionHistoryWriter = actionHistoryWriter;
        this.actualAvgSalesWriter = actualAvgSalesWriter;
        this.cyclicActionItemWriter = cyclicActionItemWriter;
        this.cyclicActionItemProcessor = cyclicActionItemProcessor;
        this.actionHistoryItemProcessor = actionHistoryItemProcessor;
        this.actualAvgSalesProcessor = actualAvgSalesProcessor;
        this.jobDiagnosticsListener = jobDiagnosticsListener;
        this.stepDiagnosticsListener = stepDiagnosticsListener;
    }

    @Bean
    public Job job() {
        return new JobBuilder("firstJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobDiagnosticsListener)
                .start(firstStep())
                .next(secondStep())
                .next(thirdStep())
                .build();
    }

    @Bean
    public Step firstStep() {
        return new StepBuilder("actionHistoryStep", jobRepository)
                .allowStartIfComplete(true)
                .<Position, Position>chunk(10000)
                .transactionManager(transactionManager)
                .reader(actionHistoryReader)
                .processor(actionHistoryItemProcessor)
                .writer(actionHistoryWriter)
                .listener(stepDiagnosticsListener)
                .build();
    }

    @Bean
    public Step secondStep() {
        return new StepBuilder("actualSalesStep", jobRepository)
                .allowStartIfComplete(true)
                .<SalesPeriod, SalesPeriod>chunk(10000)
                .transactionManager(transactionManager)
                .reader(actualAvgSalesReader)
                .processor(actualAvgSalesProcessor)
                .writer(actualAvgSalesWriter)
                .listener(stepDiagnosticsListener)
                .build();
    }

    @Bean
    public Step thirdStep() {
        return new StepBuilder("forecastStep", jobRepository)
                .allowStartIfComplete(true)
                .<Position, Position>chunk(5000)
                .transactionManager(transactionManager)
                .reader(cyclicActionListReader)
                .processor(cyclicActionItemProcessor)
                .writer(cyclicActionItemWriter)
                .listener(stepDiagnosticsListener)
                .build();
    }
}
