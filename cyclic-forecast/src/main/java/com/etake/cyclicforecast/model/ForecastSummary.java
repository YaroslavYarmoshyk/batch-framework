package com.etake.cyclicforecast.model;

import java.util.List;

public record ForecastSummary(
        int totalRows,
        List<LevelDistribution> distribution,
        List<String> noneRowDiagnostics
) {
}
