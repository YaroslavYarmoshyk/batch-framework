package com.etake.avgcheckplan.repository;

import com.etake.avgcheckplan.model.NamedRef;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LocationRepository {
    private final JdbcClient jdbcClient;

    /**
     * Resolves store locations by name that were open as of {@code asOfDate}. A store counts as
     * open when it has no {@code close_date}, or its {@code close_date} is after {@code asOfDate}.
     * Names with no matching open store are simply absent from the result.
     */
    public List<NamedRef> findStoresByName(final Collection<String> storeNames, final LocalDate asOfDate) {
        if (storeNames.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT l.id   AS id,
                               l.name AS name
                        FROM locations l
                        WHERE l.type = 'store'
                          AND (l.close_date IS NULL OR l.close_date > :asOfDate)
                          AND l.name IN (:storeNames);
                        """)
                .param("storeNames", storeNames)
                .param("asOfDate", asOfDate)
                .query(NamedRef.class)
                .list();
    }
}
