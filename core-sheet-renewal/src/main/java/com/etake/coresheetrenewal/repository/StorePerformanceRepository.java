package com.etake.coresheetrenewal.repository;

import com.etake.coresheetrenewal.model.StoreDailySales;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StorePerformanceRepository {
    private final JdbcClient jdbcClient;

    public List<StoreDailySales> getStoreDailySales(LocalDate from, LocalDate to) {
        return jdbcClient.sql(
                """

"""
        )
    }
}