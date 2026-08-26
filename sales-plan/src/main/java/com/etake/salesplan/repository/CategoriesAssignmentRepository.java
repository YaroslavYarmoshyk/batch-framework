package com.etake.salesplan.repository;

import com.etake.salesplan.model.CategoryAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CategoriesAssignmentRepository {
    private final JdbcClient jdbcClient;

    public List<CategoryAssignment> findCategoryAssignmentsByManagers() {
        return jdbcClient.sql("""
                        SELECT Concat(e.first_name, ' ', e.last_name) AS manager,
                               c.NAME                                 AS category
                        FROM   categories c
                                   JOIN employees e
                                        ON e.id = c.category_manager_id
                        WHERE  e.first_name <> N'БезМенеджера'
                        ORDER  BY Concat(e.first_name, ' ', e.last_name),
                                  c.NAME
                        """)
                .query(CategoryAssignment.class)
                .list();
    }
}
