package com.etake.turnoverplan.repository.impl;

import com.etake.turnoverplan.model.StoreCategory;
import com.etake.turnoverplan.repository.StoreCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class StoreCategoryRepositoryImpl implements StoreCategoryRepository {
    private final JdbcClient jdbcClient;

    @Override
    public List<StoreCategory> getActiveStoreCategoryPairs() {
        return jdbcClient.sql(getActiveStoreCategorySqlQuery())
                .query(StoreCategory.class)
                .list();
    }

    private static String getActiveStoreCategorySqlQuery() {
        return """
                SELECT DISTINCT
                    l.name AS storeName,
                    c.name AS categoryName,
                    r.name AS regionName
                FROM
                    locations l
                        LEFT JOIN
                    regions r ON r.id = l.region_id
                        CROSS JOIN
                    categories c
                WHERE
                    l.close_date is NULL
                  AND c.name NOT IN ('0-Без категорії', '0-Військова тематика', '0-Креатив', '0-Рибалка')
                  AND r.name != 'Внутрішній';
                """;
    }
}
