package com.etake.salesplan.service;

import com.etake.salesplan.model.Sales;
import com.etake.salesplan.model.StoreCategoryAveragedSales;
import com.etake.salesplan.model.StoreCategorySales;
import com.etake.salesplan.model.enumeration.Period;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.etake.salesplan.constants.CalculationConstants.PRECISE_MATH_CONTEXT;
import static java.util.stream.Collectors.toMap;

@Service
public class AverageSalesService {

    public List<StoreCategoryAveragedSales> getAveragedSales(List<StoreCategorySales> storeCategorySales) {
        return storeCategorySales.stream()
                .map(AverageSalesService::getAveragedSales)
                .toList();
    }

    private static StoreCategoryAveragedSales getAveragedSales(StoreCategorySales storeCategorySales) {
        Map<Period, Sales> averagedSales = storeCategorySales.sales().entrySet().stream()
                .collect(toMap(
                        Map.Entry::getKey,
                        entry -> getAveragedSales(entry.getValue().values())
                ));
        return new StoreCategoryAveragedSales(
                storeCategorySales.key(),
                storeCategorySales.sales(),
                averagedSales
        );
    }

    private static Sales getAveragedSales(Collection<Sales> sales) {
        BigDecimal turnoverSum = BigDecimal.ZERO;
        BigDecimal marginSum = BigDecimal.ZERO;
        int daysCount = 0;
        for (Sales sale : sales) {
            BigDecimal t = sale.turnover();
            turnoverSum = turnoverSum.add(t);
            marginSum = marginSum.add(sale.margin());

            if (t.signum() > 0) {
                daysCount++;
            }
        }
        if (daysCount == 0) {
            return new Sales(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal divisor = BigDecimal.valueOf(daysCount);
        return new Sales(
                turnoverSum.divide(divisor, PRECISE_MATH_CONTEXT),
                marginSum.divide(divisor, PRECISE_MATH_CONTEXT)
        );
    }
}
