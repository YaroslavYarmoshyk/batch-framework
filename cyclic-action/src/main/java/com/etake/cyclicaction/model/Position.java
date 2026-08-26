package com.etake.cyclicaction.model;

import com.etake.cyclicaction.enumeration.Algorithm;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class Position {
    private String name;
    private String store;
    private String manager;
    private Integer carryover;
    private Integer actionCode;
    private Integer storeFormat;
    private String thirdGroup;
    private String actionType;
    private LocalDate actionStartDate;
    private LocalDate actionEndDate;
    private LocalDate beforeActionStartDate;
    private LocalDate beforeActionEndDate;
    private BigDecimal actionAverageSales;
    private BigDecimal beforeActionAverageSales;
    private BigDecimal actualAverageSales;
    private Algorithm algorithm;
}
