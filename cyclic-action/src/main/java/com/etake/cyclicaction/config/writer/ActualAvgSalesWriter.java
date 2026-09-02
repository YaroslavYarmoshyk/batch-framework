package com.etake.cyclicaction.config.writer;

import com.etake.cyclicaction.dao.CyclicActionState;
import com.etake.cyclicaction.model.SalesPeriod;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActualAvgSalesWriter implements ItemStreamWriter<SalesPeriod> {
    private final CyclicActionState cyclicActionState;

    @Override
    public void write(@NonNull final Chunk<? extends SalesPeriod> chunk) {
        cyclicActionState.addPeriodsToActualAvgSales(chunk.getItems());
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
