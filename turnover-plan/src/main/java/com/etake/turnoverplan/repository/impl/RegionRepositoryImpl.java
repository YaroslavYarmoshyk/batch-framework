package com.etake.turnoverplan.repository.impl;

import com.etake.turnoverplan.model.RegionOrder;
import com.etake.turnoverplan.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RegionRepositoryImpl implements RegionRepository {
    private final JdbcClient jdbcClient;

    @Override
    public List<RegionOrder> findAllRegionOrders() {
        return jdbcClient.sql(findAllRegionOrdersSqlQuery())
                .query(RegionOrder.class)
                .list();
    }

    private static String findAllRegionOrdersSqlQuery() {
        return """
                SELECT r.sort_order, r.name
                FROM regions r;
                """;
    }
}
