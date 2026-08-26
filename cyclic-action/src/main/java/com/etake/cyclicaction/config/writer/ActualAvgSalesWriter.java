package com.etake.cyclicaction.config.writer;

import com.etake.cyclicaction.dao.InMemoryStore;
import com.etake.cyclicaction.model.SalesPeriod;
import lombok.NonNull;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.stereotype.Component;

@Component
public class ActualAvgSalesWriter implements ItemStreamWriter<SalesPeriod> {

    @Override
    public void write(@NonNull final Chunk<? extends SalesPeriod> chunk) {
        InMemoryStore.addPeriodsToActualAvgSales(chunk.getItems());
    }

    @Override
    public void open(@NonNull final ExecutionContext executionContext) throws ItemStreamException {
        ItemStreamWriter.super.open(executionContext);
    }

    @Override
    public void update(@NonNull final ExecutionContext executionContext) throws ItemStreamException {
        ItemStreamWriter.super.update(executionContext);
    }

    @Override
    public void close() throws ItemStreamException {
        ItemStreamWriter.super.close();
    }
}
