package com.etake.cyclicforecast.repository;

import com.etake.cyclicforecast.model.NamedRef;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class LocationRepository {
    private final JdbcClient jdbcClient;

    public List<NamedRef> findNamesByIds(final Collection<String> storeIds) {
        if (storeIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT l.id   AS id,
                               l.name AS name
                        FROM locations l
                        WHERE l.id IN (:storeIds)
                        """)
                .param("storeIds", storeIds)
                .query(NamedRef.class)
                .list();
    }
}
