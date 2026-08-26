package com.etake.cyclicaction.dao;

import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.model.SalesPeriod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryStore {
    public static List<Position> actionHistory = new ArrayList<>();
    public static List<Position> cyclicAction = new ArrayList<>();
    public static List<SalesPeriod> actualAvgSales = new ArrayList<>();

    public static void addPositionsToHistory(final Collection<? extends Position> positions) {
        actionHistory.addAll(positions);
    }

    public static void addPositionToActualAction(final Position position) {
        cyclicAction.add(position);
    }

    public static void addPeriodsToActualAvgSales(final Collection<? extends SalesPeriod> salesPeriod) {
        actualAvgSales.addAll(salesPeriod);
    }

    public static Set<Integer> getActionCodes() {
        return cyclicAction.stream()
                .map(Position::getActionCode)
                .collect(Collectors.toSet());
    }
}
