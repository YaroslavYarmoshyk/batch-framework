package com.etake.salesplan.repository;

import com.etake.salesplan.model.DailySalesPlansRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class DailyStoreCategoryPlanRepository {
    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void replaceMonthlyPlans(int year, int month, List<DailySalesPlansRecord> records) {
        log.info("Replacing plans for {}-{}, record count: {}", year, month, records.size());

        try {
            // Step 1: Delete existing month data
            int deletedCount = deleteByYearMonth(year, month);
            log.info("Deleted {} existing records for {}-{}", deletedCount, year, month);

            // Step 2: Bulk insert new data
            if (!records.isEmpty()) {
                bulkInsertPlans(records);
                log.info("Inserted {} new records for {}-{}", records.size(), year, month);
            } else {
                log.info("No new records to insert for {}-{}", year, month);
            }

        } catch (Exception e) {
            log.error("Failed to replace plans for {}-{}: {}", year, month, e.getMessage(), e);
            throw e;
        }
    }

    private int deleteByYearMonth(int year, int month) {
        // Convert year-month to date range for efficient index usage
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        String deleteSql = """
            DELETE FROM daily_sales_plans
            WHERE date >= ? AND date <= ?
            """;
        return jdbcClient.sql(deleteSql)
                .param(Date.valueOf(startDate))
                .param(Date.valueOf(endDate))
                .update();
    }

    private void bulkInsertPlans(List<DailySalesPlansRecord> records) {
        String insertSql = """
            INSERT INTO daily_sales_plans (store_id, category_id, date, turnover, margin)
            VALUES (?, ?, ?, ?, ?)
            """;

        // Process in chunks for better performance and memory management
        int chunkSize = 300;
        for (int i = 0; i < records.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, records.size());
            List<DailySalesPlansRecord> chunk = records.subList(i, end);

            log.debug("Processing chunk {}-{} of {} total records", i + 1, end, records.size());

            // Use JdbcTemplate for batch operations as JdbcClient doesn't support batchUpdate yet
            jdbcTemplate.batchUpdate(insertSql, chunk, chunk.size(), (ps, record) -> {
                ps.setString(1, record.storeId());
                ps.setString(2, record.categoryId());
                ps.setDate(3, Date.valueOf(record.date()));
                ps.setBigDecimal(4, record.turnover());
                ps.setBigDecimal(5, record.margin());
            });

            log.debug("Batch update completed for chunk, {} records processed", chunk.size());
        }
    }
}
