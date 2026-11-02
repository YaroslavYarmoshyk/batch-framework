package com.etake.promotionforecast.config;

import com.etake.promotionforecast.mapper.ReportRowMapper;
import com.etake.promotionforecast.model.ForecastPosition;
import com.etake.promotionforecast.model.ReportRow;
import com.etake.promotionforecast.service.GoogleSheetsUploadService;
import com.etake.promotionforecast.service.PromotionPositionsService;
import com.etake.promotionforecast.service.PromotionSheetsLayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionForecastTasklet implements Tasklet {
    private final PromotionPositionsService promotionPositionsService;
    private final ReportRowMapper reportRowMapper;
    private final GoogleSheetsUploadService googleSheetsUploadService;
    private final PromotionSheetsLayoutService promotionSheetsLayoutService;

    @Override
    public RepeatStatus execute(@NonNull final StepContribution contribution, @NonNull final ChunkContext chunkContext) throws Exception {
        final List<ForecastPosition> positions = promotionPositionsService.forecast();

        final List<ReportRow> reportRows = reportRowMapper.map(positions);

        googleSheetsUploadService.upload(reportRows);
        promotionSheetsLayoutService.rebuild(reportRows);

        return RepeatStatus.FINISHED;
    }
}
