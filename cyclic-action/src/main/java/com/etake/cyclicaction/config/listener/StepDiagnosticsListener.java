package com.etake.cyclicaction.config.listener;

import com.etake.cyclicaction.dao.InMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StepDiagnosticsListener implements StepExecutionListener {

    @Override
    public void beforeStep(final StepExecution stepExecution) {
        log.info("Starting step [{}]", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(final StepExecution stepExecution) {
        log.info("Step [{}] finished with status {}: read={}, written={}, filtered={}, skipped(read/process/write)={}/{}/{}",
                stepExecution.getStepName(), stepExecution.getStatus(),
                stepExecution.getReadCount(), stepExecution.getWriteCount(), stepExecution.getFilterCount(),
                stepExecution.getReadSkipCount(), stepExecution.getProcessSkipCount(), stepExecution.getWriteSkipCount());
        log.info("InMemoryStore snapshot after [{}]: actionHistory={}, cyclicAction={}, actualAvgSales={}",
                stepExecution.getStepName(), InMemoryStore.actionHistory.size(),
                InMemoryStore.cyclicAction.size(), InMemoryStore.actualAvgSales.size());
        return stepExecution.getExitStatus();
    }
}
