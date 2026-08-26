package com.etake.cyclicaction.config.mapper;

import com.etake.cyclicaction.model.SalesPeriod;
import org.springframework.batch.extensions.excel.RowMapper;
import org.springframework.batch.extensions.excel.support.rowset.RowSet;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Properties;

import static com.etake.cyclicaction.util.Constants.ACTUAL_AVERAGE_SALES;
import static com.etake.cyclicaction.util.Constants.ACTUAL_SALES_CODE;
import static com.etake.cyclicaction.util.Constants.ACTUAL_SALES_STORE;
import static com.etake.cyclicaction.util.Constants.REDUNDANT_CHARACTERS_IN_STORE_NAME;
import static com.etake.cyclicaction.util.CyclicUtil.isForMapping;
import static com.excel.custom.library.util.MappingUtils.castToType;

@Component
public class SalesPeriodRowMapper implements RowMapper<SalesPeriod> {

    @Override
    public SalesPeriod mapRow(final RowSet rowSet) {
        if (isForMapping(rowSet, ACTUAL_SALES_CODE)) {
            return map(rowSet);
        }
        return null;
    }

    private SalesPeriod map(final RowSet rowSet) {
        final Properties properties = rowSet.getProperties();
        return new SalesPeriod(
                getValidStoreName(properties),
                castToType(properties.get(ACTUAL_SALES_CODE), Integer.class),
                castToType(properties.get(ACTUAL_AVERAGE_SALES), BigDecimal.class)
        );
    }

    private static String getValidStoreName(final Properties properties) {
        return Optional.ofNullable(castToType(properties.get(ACTUAL_SALES_STORE), String.class))
                .map(stockName -> stockName.replaceAll(REDUNDANT_CHARACTERS_IN_STORE_NAME, ""))
                .orElse(null);
    }
}
