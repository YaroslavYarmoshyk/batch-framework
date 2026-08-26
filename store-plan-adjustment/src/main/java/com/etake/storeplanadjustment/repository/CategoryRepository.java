package com.etake.storeplanadjustment.repository;

import com.etake.storeplanadjustment.model.NamedRef;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CategoryRepository {
    private final JdbcClient jdbcClient;

    /**
     * Resolves category names for the given {@code daily_sales_plans.category_id} values.
     */
    public List<NamedRef> findCategoriesById(final Collection<String> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT DISTINCT cc.category_id   AS id,
                                        cc.category_name AS name
                        FROM category_classification cc
                        WHERE cc.category_id IN (:categoryIds);
                        """)
                .param("categoryIds", categoryIds)
                .query(NamedRef.class)
                .list();
    }
}
