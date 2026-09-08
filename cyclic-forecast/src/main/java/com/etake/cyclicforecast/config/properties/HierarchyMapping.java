package com.etake.cyclicforecast.config.properties;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Documents the category_classification climb used by the cascade (Level 3/4 group = third_subcategory_id,
 * Level 5 group = second_subcategory_id). SQL Server cannot parameterize column identifiers, so these
 * values are not read by the repository queries — they centralize the mapping as a single source of truth
 * instead of leaving it implicit in SQL text blocks.
 */
public record HierarchyMapping(
        @DefaultValue("third_subcategory_id") String level3Column,
        @DefaultValue("second_subcategory_id") String level2Column
) {
}
