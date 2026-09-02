package com.etake.cyclicaction.config.processor;

import com.etake.cyclicaction.dao.CyclicActionState;
import com.etake.cyclicaction.model.SalesPeriod;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@StepScope
@RequiredArgsConstructor
public class ActualAvgSalesProcessor implements ItemProcessor<SalesPeriod, SalesPeriod> {
    private final CyclicActionState cyclicActionState;

    private Set<Integer> actionCodes;

    @PostConstruct
    private void setActionCodes() {
        actionCodes = cyclicActionState.getActionCodes();
    }

    @Override
    public SalesPeriod process(final SalesPeriod item) {
        if (actionCodes.contains(item.actionCode())) {
            return item;
        }
        return null;
    }
}
