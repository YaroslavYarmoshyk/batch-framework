package com.etake.avgcheckplan.config;

import com.etake.avgcheckplan.config.properties.ForecastStrategy;
import com.etake.avgcheckplan.service.PlanedMonthAvgCheckForecastService;
import com.etake.avgcheckplan.service.SeasonalCoefficientAvgCheckForecastService;
import com.etake.avgcheckplan.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvgCheckTasklet implements Tasklet {
    private final PlanedMonthAvgCheckForecastService planedMonthAvgCheckForecastService;
    private final SeasonalCoefficientAvgCheckForecastService seasonalCoefficientAvgCheckForecastService;
    private final SystemConfigurationProperties systemConfigurationProperties;
    private final ExcelService excelService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {

        if (systemConfigurationProperties.forecastStrategy() == ForecastStrategy.SEASONAL_COEFFICIENT) {
            excelService.writeSeasonalWorkbook(seasonalCoefficientAvgCheckForecastService.getForecastPositions());
        } else {
            excelService.writeWorkbook(planedMonthAvgCheckForecastService.getForecastPositions());
        }

        return RepeatStatus.FINISHED;
    }
}
