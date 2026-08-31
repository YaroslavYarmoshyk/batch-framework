package com.etake.cyclicaction.config.processor;

import com.etake.cyclicaction.dao.InMemoryStore;
import com.etake.cyclicaction.enumeration.ActionType;
import com.etake.cyclicaction.model.Position;
import lombok.NonNull;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

import static com.etake.cyclicaction.util.CyclicUtil.isAnalyzedPosition;

@Configuration
public class ActionHistoryItemProcessor implements ItemProcessor<Position, Position> {
    @Value("${cyclic-action.start-date}")
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate actionStartDate;
    @Value("${cyclic-action.end-date}")
    @DateTimeFormat(pattern = "dd.MM.yyyy")
    private LocalDate actionEndDate;

    @Override
    public Position process(@NonNull final Position position) {
        if (!ActionType.isCyclic(position.getActionType())) {
            return null;
        }
        if (isAnalyzedPosition(position, actionStartDate, actionEndDate)) {
            InMemoryStore.addPositionToActualAction(position);
            return null;
        }
        return position;
    }
}
