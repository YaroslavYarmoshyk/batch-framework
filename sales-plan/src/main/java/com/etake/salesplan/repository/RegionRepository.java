package com.etake.salesplan.repository;

import com.etake.salesplan.model.RegionOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RegionRepository {
    private final JdbcClient jdbcClient;

    public List<RegionOrder> findAllRegionOrders() {
        return jdbcClient.sql("""
                        SELECT r.sort_order, r.id, r.name
                        FROM regions r;
                        """)
                .query(RegionOrder.class)
                .list();
    }
}
