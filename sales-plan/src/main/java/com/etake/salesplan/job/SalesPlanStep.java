package com.etake.salesplan.job;

import com.etake.salesplan.service.StoreCategorySalesService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SalesPlanStep implements Step {
    private final StoreCategorySalesService storeCategorySalesService;

    @NonNull
    @Override
    public String getName() {
        return "salesPlanStep";
    }

    @Override
    public void execute(@NonNull StepExecution stepExecution) {
        try {
            storeCategorySalesService.getSales();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
