package com.etake.turnoverplan.config.properties;

import com.etake.turnoverplan.config.indices.CategoriesSheetColumnIndices;
import com.etake.turnoverplan.config.indices.DataSheetColumnIndices;
import com.etake.turnoverplan.config.indices.StoresSheetColumnIndices;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "system-configuration.column-indices")
public record ColumnIndices(
        DataSheetColumnIndices dataSheet,
        CategoriesSheetColumnIndices categoriesSheet,
        StoresSheetColumnIndices storesSheet
) {
}
