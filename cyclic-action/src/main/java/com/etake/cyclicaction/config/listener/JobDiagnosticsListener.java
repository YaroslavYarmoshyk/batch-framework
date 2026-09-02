package com.etake.cyclicaction.config.listener;

import com.etake.cyclicaction.dao.CyclicActionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.observability.BatchMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@Slf4j
@RequiredArgsConstructor
public class JobDiagnosticsListener implements JobExecutionListener {
    private final CyclicActionState cyclicActionState;

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
        final String duration = BatchMetrics.formatDuration(
                BatchMetrics.calculateDuration(jobExecution.getStartTime(), jobExecution.getEndTime()));
        log.info("Job [{}] finished with status {} in {}: actionHistory={}, cyclicAction={}, actualAvgSales={}",
                jobExecution.getJobInstance().getJobName(), jobExecution.getStatus(), duration,
                cyclicActionState.getActionHistory().size(), cyclicActionState.getCyclicAction().size(),
                cyclicActionState.getActualAvgSales().size());
    }
}
