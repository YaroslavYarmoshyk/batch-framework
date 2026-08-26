package com.etake.cyclicaction.config.mapper;

import com.etake.cyclicaction.model.Position;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.extensions.excel.RowMapper;
import org.springframework.batch.extensions.excel.support.rowset.RowSet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Properties;

import static com.etake.cyclicaction.util.Constants.*;
import static com.etake.cyclicaction.util.CyclicUtil.isForMapping;
import static com.excel.custom.library.util.MappingUtils.castToType;

@Slf4j
@Component
@RequiredArgsConstructor
public class PositionRowMapper implements RowMapper<Position> {

    @Override
    public Position mapRow(final RowSet rowSet) {
        if (isForMapping(rowSet, ACTION_CODE)) {
            return map(rowSet);
        }
        return null;
    }

    private Position map(final RowSet rowSet) {
        final Properties properties = rowSet.getProperties();
        return Position.builder()
                .name(castToType(properties.get(NAME), String.class))
                .store(castToType(properties.get(STORE), String.class))
                .manager(castToType(properties.get(MANAGER), String.class))
                .carryover(castToType(properties.get(CARRYOVER), Integer.class))
                .actionCode(castToType(properties.get(ACTION_CODE), Integer.class))
                .storeFormat(castToType(properties.get(STORE_FORMAT), Integer.class))
                .thirdGroup(castToType(properties.get(THIRD_GROUP), String.class))
                .actionType(castToType(properties.get(ACTION_TYPE), String.class))
                .actionStartDate(castToType(properties.get(ACTION_START_DATE), LocalDate.class))
                .actionEndDate(castToType(properties.get(ACTION_END_DATE), LocalDate.class))
                .beforeActionStartDate(castToType(properties.get(BEFORE_ACTION_START_DATE), LocalDate.class))
                .beforeActionEndDate(castToType(properties.get(BEFORE_ACTION_END_DATE), LocalDate.class))
                .actionAverageSales(castToType(properties.get(ACTION_AVERAGE_SALES), BigDecimal.class))
                .beforeActionAverageSales(castToType(properties.get(BEFORE_ACTION_AVERAGE_SALES), BigDecimal.class))
                .build();
    }
}

