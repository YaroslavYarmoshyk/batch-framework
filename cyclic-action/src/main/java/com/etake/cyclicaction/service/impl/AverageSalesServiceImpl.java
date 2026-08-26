package com.etake.cyclicaction.service.impl;

import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.service.AverageSalesService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.excel.custom.library.util.Constants.DEFAULT_AVERAGE_SALES_SCALE;
import static com.excel.custom.library.util.Constants.DEFAULT_ROUNDING_MODE;

@Service
public class AverageSalesServiceImpl implements AverageSalesService {

    @Override
    public BigDecimal getAverageSales(final List<Position> positionsByAlgorithm,
                                      final Function<Position, BigDecimal> avgSalesFunc) {
        final double avgSales = positionsByAlgorithm.stream()
                .map(avgSalesFunc)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0.0);
        return BigDecimal.valueOf(avgSales)
                .setScale(DEFAULT_AVERAGE_SALES_SCALE, DEFAULT_ROUNDING_MODE);
    }

    @Override
    public BigDecimal getExactAverageSales(final List<Position> positionsByAlgorithm,
                                           final Function<Position, BigDecimal> avgSalesFunc) {
        final List<Position> filteredPositions = positionsByAlgorithm.stream()
                .filter(pos -> Objects.nonNull(pos.getBeforeActionAverageSales())
                        && Objects.nonNull(pos.getActionAverageSales())).toList();
        return getAverageSales(filteredPositions, avgSalesFunc);
    }
}
