package com.etake.shelvesdistribution.config;

import com.etake.shelvesdistribution.service.ShelvesDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ShelvesDistributionStep implements Step {
    private final ShelvesDistributionService shelvesDistributionService;

    @NonNull
    @Override
    public String getName() {
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
