package com.etake.cyclicaction.config.listener;

import com.etake.cyclicaction.dao.InMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
public class JobDiagnosticsListener implements JobExecutionListener {
    @Value("${cyclic-action.start-date}")
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate actionStartDate;
    @Value("${cyclic-action.end-date}")
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate actionEndDate;

    @Override
    public void beforeJob(final JobExecution jobExecution) {
        log.info("Starting job [{}] filtering action dates {} - {}",
                jobExecution.getJobInstance().getJobName(), actionStartDate, actionEndDate);
    }

    @Override
    public void afterJob(final JobExecution jobExecution) {
        log.info("Job [{}] finished with status {}: actionHistory={}, cyclicAction={}, actualAvgSales={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(),
                InMemoryStore.actionHistory.size(), InMemoryStore.cyclicAction.size(),
                InMemoryStore.actualAvgSales.size());
    }
}
