package com.etake.cyclicaction.config.writer;

import com.etake.cyclicaction.dao.CyclicActionState;
import com.etake.cyclicaction.model.Position;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActionHistoryWriter implements ItemStreamWriter<Position> {
    private final CyclicActionState cyclicActionState;

    @Override
    public void write(@NonNull final Chunk<? extends Position> chunk) {
        cyclicActionState.addPositionsToHistory(chunk.getItems());
    }

    @Override
    public void open(@NonNull final ExecutionContext executionContext) throws ItemStreamException {

    }

    @Override
    public void update(@NonNull final ExecutionContext executionContext) throws ItemStreamException {

    }

}
