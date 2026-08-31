package com.etake.shelvesdistribution.config;

import com.etake.shelvesdistribution.service.ShelvesDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ShelvesDistributionStep implements Step {
    private final ShelvesDistributionService shelvesDistributionService;

    @Override
    public @NonNull String getName() {
        return "shelvesDistributionStep";
    }

    @Override
    public void execute(@NonNull StepExecution stepExecution) {
        try {
            shelvesDistributionService.generateShelvesDistributionReport();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
