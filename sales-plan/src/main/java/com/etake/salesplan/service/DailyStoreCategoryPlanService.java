package com.etake.salesplan.service;

import com.etake.salesplan.config.YearMonthProperties;
import com.etake.salesplan.model.DailySales;
import com.etake.salesplan.model.DailySalesPlansRecord;
import com.etake.salesplan.repository.DailyStoreCategoryPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyStoreCategoryPlanService {
    private final DailyStoreCategoryPlanRepository dailyStoreCategoryPlanRepository;
    private final YearMonthProperties yearMonthProperties;

    public void uploadPlans(List<DailySales> salesPlans) {
        int plansCount = salesPlans.size();
        log.info("Starting upload of daily sales plans for {}-{}. Plans count = {}",
                yearMonthProperties.year(), yearMonthProperties.month(), plansCount);

        List<DailySalesPlansRecord> dailySalesPlansRecords = salesPlans.stream()
                .flatMap(salesPlan ->
                        salesPlan.sales().entrySet().stream()
                                .map(dailySales -> new DailySalesPlansRecord(
                                        salesPlan.storeId(),
                                        salesPlan.categoryId(),
                                        dailySales.getKey(),
                                        dailySales.getValue().turnover(),
                                        dailySales.getValue().margin()
                                ))
                )
                .toList();

        long startTime = System.currentTimeMillis();

        dailyStoreCategoryPlanRepository.replaceMonthlyPlans(
                yearMonthProperties.year(),
                yearMonthProperties.month(),
                dailySalesPlansRecords
        );

        long duration = System.currentTimeMillis() - startTime;
        log.info("Saving data into DB took: {} seconds", duration / 1000.0);

        log.info("Successfully uploaded daily sales plans for {}-{}. Plans count = {}, records count = {}",
                yearMonthProperties.year(), yearMonthProperties.month(), plansCount, dailySalesPlansRecords.size());
    }
}
