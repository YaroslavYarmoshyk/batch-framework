package com.etake.cyclicaction.service;

import com.etake.cyclicaction.model.Position;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

public interface AverageSalesService {
    BigDecimal getAverageSales(final List<Position> positionsByAlgorithm, final Function<Position, BigDecimal> avgSalesFunc);

    BigDecimal getExactAverageSales(final List<Position> positionsByAlgorithm, final Function<Position, BigDecimal> avgSalesFunc);
}
