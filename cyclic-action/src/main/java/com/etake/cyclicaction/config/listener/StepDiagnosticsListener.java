package com.etake.cyclicaction.config.listener;

import com.etake.cyclicaction.dao.CyclicActionState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.observability.BatchMetrics;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StepDiagnosticsListener implements StepExecutionListener {
    private final CyclicActionState cyclicActionState;

    @Override
    public void beforeStep(final StepExecution stepExecution) {
        log.info("Starting step [{}]", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(final StepExecution stepExecution) {
        final String duration = BatchMetrics.formatDuration(
                BatchMetrics.calculateDuration(stepExecution.getStartTime(), stepExecution.getEndTime()));
        log.info("Step [{}] finished with status {} in {}: read={}, written={}, filtered={}, skipped(read/process/write)={}/{}/{}",
                stepExecution.getStepName(), stepExecution.getStatus(), duration,
                stepExecution.getReadCount(), stepExecution.getWriteCount(), stepExecution.getFilterCount(),
                stepExecution.getReadSkipCount(), stepExecution.getProcessSkipCount(), stepExecution.getWriteSkipCount());
        log.info("CyclicActionState snapshot after [{}]: actionHistory={}, cyclicAction={}, actualAvgSales={}",
                stepExecution.getStepName(), cyclicActionState.getActionHistory().size(),
                cyclicActionState.getCyclicAction().size(), cyclicActionState.getActualAvgSales().size());
        return stepExecution.getExitStatus();
    }
}
