package com.etake.avgcheckplan.repository;

import com.etake.avgcheckplan.model.CheckPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CheckPositionRepository {
    private final JdbcClient jdbcClient;

    public List<CheckPosition> findAllInPeriod(final LocalDate fromDate, final LocalDate toDate) {
        final String sql = """
                DECLARE @startDate DATE = :fromDate;
                DECLARE @endDate DATE = :toDate;
                DECLARE @startDatetime DATETIME = CAST(@startDate AS DATETIME);
                DECLARE @endDatetime DATETIME = DATEADD(SECOND, -1, DATEADD(DAY, 1, CAST(@endDate AS DATETIME)));
                
                SELECT t.check_id                      AS id,
                       CAST(t.datetime AS DATE)        AS date,
                       r.name AS region_name,
                       l.name                          AS store_name,
                       CASE
                           WHEN SUM(t.units) < 0 THEN 1
                           ELSE 0
                           END                         AS refunded,
                       SUM(t.units * t.discount_price) AS amount
                FROM transactions t
                         LEFT JOIN locations l on l.id = t.location_id
                LEFT JOIN regions r on l.region_id = r.id
                WHERE t.datetime BETWEEN @startDatetime AND @endDatetime
                  AND l.close_date is null AND l.type = 'store'
                GROUP BY t.check_id,
                         CAST(t.datetime AS DATE),
                         r.name,
                         l.name;
                
                """;
        return jdbcClient.sql(sql)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .query(CheckPosition.class)
                .list();
    }

    public List<CheckPosition> findAllInCurrentPeriod() {
        final String sql = """
                DECLARE @lastTransactionDate DATE = (SELECT TOP 1 CAST(datetime AS DATE) FROM transactions ORDER BY datetime DESC);
                DECLARE @endDate DATE = DATEADD(DAY, -1, @lastTransactionDate);
                DECLARE @startDate DATE = DATEADD(MONTH, DATEDIFF(MONTH, 0, @endDate), 0);
                
                DECLARE @startDatetime DATETIME = CAST(@startDate AS DATETIME);
                DECLARE @endDatetime DATETIME = DATEADD(SECOND, -1, DATEADD(DAY, 1, CAST(@endDate AS DATETIME)));
                
                SELECT t.check_id                      AS id,
                       CAST(t.datetime AS DATE)        AS date,
                       r.name AS region_name,
                       l.name                          AS store_name,
                       CASE
                           WHEN SUM(t.units) < 0 THEN 1
                           ELSE 0
                           END                         AS refunded,
                       SUM(t.units * t.discount_price) AS amount
                FROM transactions t
                         LEFT JOIN locations l on l.id = t.location_id
                LEFT JOIN regions r on l.region_id = r.id
                WHERE t.datetime BETWEEN @startDatetime AND @endDatetime
                  AND l.close_date is null
                GROUP BY t.check_id,
                         CAST(t.datetime AS DATE),
                         r.name,
                         l.name;
                
                """;
        return jdbcClient.sql(sql)
                .query(CheckPosition.class)
                .list();
    }
}
