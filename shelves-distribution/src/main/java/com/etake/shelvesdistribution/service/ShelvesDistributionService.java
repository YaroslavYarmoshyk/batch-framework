package com.etake.shelvesdistribution.service;

import com.etake.shelvesdistribution.config.properties.ShelvesDistributionProperties;
import com.etake.shelvesdistribution.model.CategoryPerformance;
import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

import static java.util.stream.Collectors.toMap;

@Service
@RequiredArgsConstructor
public class ShelvesDistributionService {
    private final ShelvesDistributionProperties shelvesDistributionProperties;
    private final ObjectProvider<StoreCategoryService> services;
    private final ExcelService excelService;

    public void generateShelvesDistributionReport() throws IOException {
        StoreCategoryService storeCategoryService = services.stream()
                .filter(service -> service.getGranularity() == shelvesDistributionProperties.granularity())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unexpected implementation"));
        List<StoreCategoryPerformance> storeCategories = storeCategoryService.getStoreCategoryPerformance();
        if (shelvesDistributionProperties.groupedByStore()) {
            excelService.generateReport(storeCategories);
        } else {
            List<CategoryPerformance> categories = aggregateByCategory(storeCategories);
            excelService.generateReport(categories, shelvesDistributionProperties.region());
        }
    }

    private static List<CategoryPerformance> aggregateByCategory(List<StoreCategoryPerformance> storeCategories) {
        return storeCategories.stream()
                .collect(toMap(
                        StoreCategoryPerformance::category,
                        s -> new CategoryPerformance(s.category(), s.discountSales(), s.costSales(), s.currentCostBalance()),
                        (a, b) -> new CategoryPerformance(a.category(),
                                a.discountSales().add(b.discountSales()),
                                a.costSales().add(b.costSales()),
                                a.currentCostBalance().add(b.currentCostBalance()))))
                .values()
                .stream()
                .toList();
    }
}
