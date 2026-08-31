package com.etake.cyclicaction.config.reader;

import com.etake.cyclicaction.dao.InMemoryStore;
import com.etake.cyclicaction.model.Position;
import org.springframework.batch.core.configuration.annotation.StepScope;

import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ListItemReaderConfig {

    @Bean
    @StepScope
    public ListItemReader<Position> cyclicActionListReader() {
        return new ListItemReader<>(InMemoryStore.cyclicAction);
    }
}
