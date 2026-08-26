package com.etake.cyclicaction.config.properties;

import com.etake.cyclicaction.util.ResourcePaths;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "excel-management")
public class ExcelManagementProperties {
    private static final String ACTION_HISTORY_KEY = "action-history";
    private static final String ACTUAL_AVG_SALES_KEY = "actual-avg-sales";
    private Map<String, Integer> skipLines;
    private Map<String, Integer> headerRowIndex;
    private Map<String, String> input;
    private Map<String, String> output;

    public int getActionHistorySkipLines() {
        return skipLines.get(ACTION_HISTORY_KEY);
    }

    public int getActionHistoryHeaderRowIndex() {
        return headerRowIndex.get(ACTION_HISTORY_KEY);
    }

    public String getActionHistoryFilePath() {
        return ResourcePaths.resolve(input.get(ACTION_HISTORY_KEY)).getAbsolutePath();
    }

    public File getCyclicActionOutputDirectory() {
        return ResourcePaths.resolve(output.get(ACTION_HISTORY_KEY));
    }

    public int getAvgSalesSkipLines() {
        return skipLines.get(ACTUAL_AVG_SALES_KEY);
    }

    public int getAvgSalesHeaderRowIndex() {
        return headerRowIndex.get(ACTUAL_AVG_SALES_KEY);
    }

    public String getAvgSalesFilePath() {
        return ResourcePaths.resolve(input.get(ACTUAL_AVG_SALES_KEY)).getAbsolutePath();
    }
}
