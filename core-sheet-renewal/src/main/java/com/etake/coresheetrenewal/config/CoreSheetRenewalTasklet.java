package com.etake.coresheetrenewal.config;

import com.etake.coresheetrenewal.config.properties.CoreSheetRenewalProperties;
import com.etake.coresheetrenewal.model.StorePerformance;
import com.etake.coresheetrenewal.service.CoreSheetRenewalService;
import com.etake.coresheetrenewal.service.StorePerformanceService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CoreSheetRenewalTasklet implements Tasklet {
    private final CoreSheetRenewalProperties properties;
    private final StorePerformanceService storePerformanceService;
    private final CoreSheetRenewalService coreSheetRenewalService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) {
        final YearMonth newMonth = properties.newMonth();
        final Map<String, StorePerformance> performance = storePerformanceService.load(newMonth.minusMonths(1));

        coreSheetRenewalService.renew(newMonth, performance, storePerformanceService.loadLocations(),
                storePerformanceService.loadPlannedStores(newMonth), storePerformanceService.loadRegionOrders());

        return RepeatStatus.FINISHED;
    }
}
