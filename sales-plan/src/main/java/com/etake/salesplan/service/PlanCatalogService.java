package com.etake.salesplan.service;

import com.etake.salesplan.model.CategoryPlan;
import com.etake.salesplan.model.StorePlan;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;
import static com.etake.salesplan.utils.WeightUtils.normalizeWeights;
import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class PlanCatalogService {
    private final ExcelClasspathReader excelClasspathReader;

    public Map<String, BigDecimal> getStoreTargets() {
        return excelClasspathReader.read("plans/Stores Plan.xlsx", storePlanMapper()).stream()
                .collect(toMap(StorePlan::storeName, StorePlan::turnover, BigDecimal::add, LinkedHashMap::new));
    }

    public Map<String, BigDecimal> getCategoryTurnoverTargets() {
        return getCategoryPlans().stream()
                .collect(toMap(CategoryPlan::categoryName, CategoryPlan::turnover, BigDecimal::add, LinkedHashMap::new));
    }

    public Map<String, BigDecimal> getCategoryMarginTargets() {
        return getCategoryPlans().stream()
                .collect(toMap(CategoryPlan::categoryName, CategoryPlan::margin, BigDecimal::add, LinkedHashMap::new));
    }

    public Map<String, BigDecimal> getCategoryTurnoverWeights() {
        return normalizeWeights(getCategoryTurnoverTargets());
    }

    public Map<String, BigDecimal> getCategoryMarginRates() {
        Map<String, BigDecimal> turnover = getCategoryTurnoverTargets();
        Map<String, BigDecimal> margin = getCategoryMarginTargets();
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        for (String category : turnover.keySet()) {
            BigDecimal catT = turnover.get(category);
            BigDecimal catM = margin.get(category);
            rates.put(category, catT.signum() == 0 ? BigDecimal.ZERO : catM.divide(catT, PRECISE_MATH_CONTEXT));
        }
        return rates;
    }

    private List<CategoryPlan> getCategoryPlans() {
        return excelClasspathReader.read("plans/Categories Plan.xlsx", categoryPlanMapper());
    }

    private static Function<Row, StorePlan> storePlanMapper() {
        return (row) -> new StorePlan(
                row.getCell(0).getStringCellValue(),
                BigDecimal.valueOf(row.getCell(1).getNumericCellValue())
        );
    }

    private static Function<Row, CategoryPlan> categoryPlanMapper() {
        return (row) -> new CategoryPlan(
                row.getCell(0).getStringCellValue(),
                BigDecimal.valueOf(row.getCell(1).getNumericCellValue()),
                BigDecimal.valueOf(row.getCell(2).getNumericCellValue())
        );
    }
}
