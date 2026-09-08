package com.etake.cyclicforecast.repository;

import com.etake.cyclicforecast.model.ProductGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductHierarchyRepository {
    private final JdbcClient jdbcClient;

    public Optional<ProductGroup> findGroup(final String productId) {
        return jdbcClient.sql("""
                        SELECT p.id                     AS product_id,
                               p.name                   AS product_name,
                               cc.third_subcategory_id   AS third_subcategory_id,
                               cc.third_subcategory_name AS third_subcategory_name,
                               cc.second_subcategory_id  AS second_subcategory_id,
                               CONCAT(e.first_name, ' ', e.last_name) AS manager
                        FROM products p
                                 JOIN category_classification cc ON cc.third_subcategory_id = p.subcategory_id_3
                                 LEFT JOIN categories c ON c.id = cc.category_id
                                 LEFT JOIN employees e ON e.id = c.category_manager_id
                        WHERE p.id = :productId
                        """)
                .param("productId", productId)
                .query(ProductGroup.class)
                .optional();
    }
}
