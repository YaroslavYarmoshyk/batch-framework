package com.etake.cyclicaction.dao;

import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.model.SalesPeriod;
import lombok.Getter;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@JobScope
public class CyclicActionState {
    @Getter
    private final List<Position> actionHistory = new ArrayList<>();
    @Getter
    private final List<Position> cyclicAction = new ArrayList<>();
    @Getter
    private final List<SalesPeriod> actualAvgSales = new ArrayList<>();

    private ActionHistoryIndex historyIndex;
    private Map<StoreActionCodeKey, SalesPeriod> salesIndex;

    public void addPositionsToHistory(final Collection<? extends Position> positions) {
        actionHistory.addAll(positions);
    }

    public void addPositionToActualAction(final Position position) {
        cyclicAction.add(position);
    }

    public void addPeriodsToActualAvgSales(final Collection<? extends SalesPeriod> salesPeriod) {
        actualAvgSales.addAll(salesPeriod);
    }

    public Set<Integer> getActionCodes() {
        return cyclicAction.stream()
                .map(Position::getActionCode)
                .collect(Collectors.toSet());
    }

    public synchronized ActionHistoryIndex getHistoryIndex() {
        if (historyIndex == null) {
            historyIndex = ActionHistoryIndex.build(actionHistory);
        }
        return historyIndex;
    }

    public synchronized Map<StoreActionCodeKey, SalesPeriod> getSalesIndex() {
        if (salesIndex == null) {
            final Map<StoreActionCodeKey, SalesPeriod> index = new HashMap<>();
            for (final SalesPeriod salesPeriod : actualAvgSales) {
                index.putIfAbsent(new StoreActionCodeKey(salesPeriod.store(), salesPeriod.actionCode()), salesPeriod);
            }
            salesIndex = index;
        }
        return salesIndex;
    }
}
