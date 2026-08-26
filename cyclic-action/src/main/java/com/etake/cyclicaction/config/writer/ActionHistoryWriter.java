package com.etake.cyclicaction.config.writer;

import com.etake.cyclicaction.dao.InMemoryStore;
import com.etake.cyclicaction.model.Position;
import lombok.NonNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.stereotype.Component;

@Component
public class ActionHistoryWriter implements ItemStreamWriter<Position> {

    @Override
    public void write(@NonNull final Chunk<? extends Position> chunk) {
        InMemoryStore.addPositionsToHistory(chunk.getItems());
    }

    @Override
    public void open(@NonNull final ExecutionContext executionContext) throws ItemStreamException {

    }

    @Override
    public void update(@NonNull final ExecutionContext executionContext) throws ItemStreamException {

    }

    @Override
    public void close() throws ItemStreamException {

    }
}
