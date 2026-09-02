package com.etake.avgcheckplan.config;

import com.etake.avgcheckplan.config.properties.ForecastStrategy;
import com.etake.avgcheckplan.model.ForecastPosition;
import com.etake.avgcheckplan.model.SeasonalPlanPosition;
import com.etake.avgcheckplan.model.StoreAvgCheck;
import com.etake.avgcheckplan.service.PlanedMonthAvgCheckForecastService;
import com.etake.avgcheckplan.service.SeasonalCoefficientAvgCheckForecastService;
import com.etake.avgcheckplan.service.ExcelService;
import com.etake.avgcheckplan.service.StoreChecksPlanUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AvgCheckTasklet implements Tasklet {
    private final PlanedMonthAvgCheckForecastService planedMonthAvgCheckForecastService;
    private final SeasonalCoefficientAvgCheckForecastService seasonalCoefficientAvgCheckForecastService;
    private final SystemConfigurationProperties systemConfigurationProperties;
    private final ExcelService excelService;
    private final StoreChecksPlanUploadService storeChecksPlanUploadService;
    @Value("${system-configuration.upload-to-db}")
    private boolean uploadToDb;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {

        if (systemConfigurationProperties.forecastStrategy() == ForecastStrategy.SEASONAL_COEFFICIENT) {
            final List<SeasonalPlanPosition> positions = seasonalCoefficientAvgCheckForecastService.getForecastPositions();
            excelService.writeSeasonalWorkbook(positions);
            if (uploadToDb) {
                storeChecksPlanUploadService.upload(positions.stream()
                        .map(position -> new StoreAvgCheck(position.store(), position.plannedAvgCheck()))
                        .toList());
            }
        } else {
            final List<ForecastPosition> positions = planedMonthAvgCheckForecastService.getForecastPositions();
            excelService.writeWorkbook(positions);
            if (uploadToDb) {
                storeChecksPlanUploadService.upload(positions.stream()
                        .map(position -> new StoreAvgCheck(position.store(), position.adjustedForecastedAvgCheckLastDay()))
                        .toList());
            }
        }

        return RepeatStatus.FINISHED;
    }
}
