package com.etake.turnoverplan.model;


import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StoreCategorySales {
    private String regionName;
    private String storeName;
    private String similarStore;
    private String categoryName;
    private BigDecimal currentPeriodCurrentMonthAvgTurnover;
    private BigDecimal prevPeriodCurrentMonthAvgTurnover;
    private BigDecimal prevPeriodFirstMonthAvgTurnover;
    private BigDecimal prevPeriodSecondMonthAvgTurnover;
    private BigDecimal prevPeriodThirdMonthAvgTurnover;

    private BigDecimal currentPeriodCurrentMonthAvgMargin;
    private BigDecimal prevPeriodCurrentMonthAvgMargin;
    private BigDecimal prevPeriodFirstMonthAvgMargin;
    private BigDecimal prevPeriodSecondMonthAvgMargin;
    private BigDecimal prevPeriodThirdMonthAvgMargin;

    private Integer prevPeriodFirstMonthSalesDays;
    private Integer prevPeriodSecondMonthSalesDays;
    private Integer prevPeriodThirdMonthSalesDays;

    private Boolean isLfl;
}
