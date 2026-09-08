package com.etake.cyclicforecast.service;

import com.etake.cyclicforecast.model.AlgorithmLevel;
import com.etake.cyclicforecast.model.ForecastResult;
import com.etake.cyclicforecast.model.ForecastSummary;
import com.etake.cyclicforecast.model.LevelDistribution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SummaryReportService {

    public ForecastSummary summarize(final List<ForecastResult> results) {
        final Map<AlgorithmLevel, Long> counts = results.stream()
                .collect(Collectors.groupingBy(ForecastResult::level, Collectors.counting()));

        final List<LevelDistribution> distribution = Arrays.stream(AlgorithmLevel.values())
                .map(level -> new LevelDistribution(level, counts.getOrDefault(level, 0L)))
                .toList();

        final List<String> diagnostics = results.stream()
                .filter(result -> result.level() == AlgorithmLevel.NONE)
                .flatMap(result -> result.diagnostics().stream()
                        .map(note -> "%s/%s: %s".formatted(result.row().storeId(), result.row().productId(), note)))
                .toList();

        log.info("Forecast level distribution ({} rows): {}", results.size(), distribution);
        return new ForecastSummary(results.size(), distribution, diagnostics);
    }
}
