package com.etake.avgcheckplan.repository;

import com.etake.avgcheckplan.model.StoreCheckPlanRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class StoreChecksPlanRepository {
    private static final int CHUNK_SIZE = 300;

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void replaceMonthlyPlans(final long year, final long month, final List<StoreCheckPlanRecord> records) {
        log.info("Replacing store checks plans for {}-{}, record count: {}", year, month, records.size());

        final int deletedCount = deleteByYearMonth(year, month);
        log.info("Deleted {} existing store checks plans for {}-{}", deletedCount, year, month);

        if (!records.isEmpty()) {
            bulkInsertPlans(records);
            log.info("Inserted {} store checks plans for {}-{}", records.size(), year, month);
        } else {
            log.info("No store checks plans to insert for {}-{}", year, month);
        }
    }

    private int deleteByYearMonth(final long year, final long month) {
        return jdbcClient.sql("DELETE FROM store_checks_plans WHERE year = :year AND month = :month")
                .param("year", year)
                .param("month", month)
                .update();
    }

    private void bulkInsertPlans(final List<StoreCheckPlanRecord> records) {
        final String insertSql = """
                INSERT INTO store_checks_plans (store_id, year, month, avg_check, avg_items_per_check)
                VALUES (?, ?, ?, ?, ?)
                """;

        for (int i = 0; i < records.size(); i += CHUNK_SIZE) {
            final List<StoreCheckPlanRecord> chunk = records.subList(i, Math.min(i + CHUNK_SIZE, records.size()));
            jdbcTemplate.batchUpdate(insertSql, chunk, chunk.size(), (ps, record) -> {
                ps.setString(1, record.storeId());
                ps.setLong(2, record.year());
                ps.setLong(3, record.month());
                ps.setBigDecimal(4, record.avgCheck());
                ps.setBigDecimal(5, record.avgItemsPerCheck());
            });
        }
    }
}
