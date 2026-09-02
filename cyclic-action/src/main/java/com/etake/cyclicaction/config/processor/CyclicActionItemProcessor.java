package com.etake.cyclicaction.config.processor;

import com.etake.cyclicaction.dao.ActionHistoryIndex;
import com.etake.cyclicaction.dao.CyclicActionState;
import com.etake.cyclicaction.dao.StoreActionCodeKey;
import com.etake.cyclicaction.enumeration.Algorithm;
import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.model.SalesPeriod;
import com.etake.cyclicaction.service.AlgorithmService;
import com.etake.cyclicaction.service.AverageSalesService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.util.Pair;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static com.etake.cyclicaction.util.Constants.DEFAULT_AVERAGE_SALES_SCALE;
import static com.etake.cyclicaction.util.Constants.DEFAULT_ROUNDING_MODE;

@Component
@RequiredArgsConstructor
public class CyclicActionItemProcessor implements ItemProcessor<Position, Position> {
    private final AlgorithmService algorithmService;
    private final AverageSalesService averageSalesService;
    private final CyclicActionState cyclicActionState;

    @Override
    public Position process(@NonNull final Position position) {
        final ActionHistoryIndex historyIndex = cyclicActionState.getHistoryIndex();
        final Pair<Algorithm, List<Position>> posAlgPair = algorithmService.definePositionsByAlgorithm(position, historyIndex);
        final Algorithm algorithm = posAlgPair.getKey();
        final List<Position> positions = posAlgPair.getValue();

        final BigDecimal beforeActionAvgSales = defineAverageSales(algorithm, positions, Position::getBeforeActionAverageSales);
        final BigDecimal actionAvgSales = defineAverageSales(algorithm, positions, Position::getActionAverageSales);
        final BigDecimal actualAverageSales = getActualAverageSales(position, cyclicActionState.getSalesIndex());

        position.setAlgorithm(algorithm);
        position.setBeforeActionAverageSales(beforeActionAvgSales);
        position.setActionAverageSales(actionAvgSales);
        position.setActualAverageSales(actualAverageSales);

        return position;
    }

    private BigDecimal defineAverageSales(final Algorithm algorithm,
                                          final List<Position> positions,
                                          final Function<Position, BigDecimal> avgSalesFunc) {
        final boolean exactAlgorithm = Algorithm.isAlgorithmExact(algorithm);
        final boolean exactSales = salesExistBeforeAndDuringAction(positions);
        return exactAlgorithm && exactSales ?
                averageSalesService.getExactAverageSales(positions, avgSalesFunc) :
                averageSalesService.getAverageSales(positions, avgSalesFunc);
    }

    private BigDecimal getActualAverageSales(final Position position, final Map<StoreActionCodeKey, SalesPeriod> salesIndex) {
        final StoreActionCodeKey key = new StoreActionCodeKey(position.getStore(), position.getActionCode());
        return Optional.ofNullable(salesIndex.get(key))
                .map(SalesPeriod::actionAverageSales)
                .map(bigDecimal -> bigDecimal.setScale(DEFAULT_AVERAGE_SALES_SCALE, DEFAULT_ROUNDING_MODE))
                .orElse(BigDecimal.ZERO);
    }

    private boolean salesExistBeforeAndDuringAction(final List<Position> positions) {
        return positions.stream()
                .anyMatch(pos -> Objects.nonNull(pos.getBeforeActionAverageSales())
                        && Objects.nonNull(pos.getActionAverageSales()));
    }
}
