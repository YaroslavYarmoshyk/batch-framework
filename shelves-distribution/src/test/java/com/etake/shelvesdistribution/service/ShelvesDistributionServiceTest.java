package com.etake.shelvesdistribution.service;

import com.etake.shelvesdistribution.config.properties.ShelvesDistributionProperties;
import com.etake.shelvesdistribution.model.CategoryPerformance;
import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import com.etake.shelvesdistribution.model.enumeration.Granularity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShelvesDistributionServiceTest {

    @Mock
    private ObjectProvider<StoreCategoryService> services;
    @Mock
    private StoreCategoryService storeCategoryService;
    @Mock
    private ExcelService excelService;

    @Test
    void generateShelvesDistributionReport_notGroupedByStore_sumsPerformanceAcrossStoresPerCategory() throws Exception {
        when(services.stream()).thenReturn(Stream.of(storeCategoryService));
        when(storeCategoryService.getGranularity()).thenReturn(Granularity.REGION);
        when(storeCategoryService.getStoreCategoryPerformance()).thenReturn(List.of(
                new StoreCategoryPerformance("Store A", "Toys", new BigDecimal("100"), new BigDecimal("60"), new BigDecimal("10")),
                new StoreCategoryPerformance("Store B", "Toys", new BigDecimal("50"), new BigDecimal("20"), new BigDecimal("5")),
                new StoreCategoryPerformance("Store A", "Books", new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("2"))
        ));
        ShelvesDistributionProperties properties = new ShelvesDistributionProperties(false, Granularity.REGION, "West", List.of());
        ShelvesDistributionService service = new ShelvesDistributionService(properties, services, excelService);

        service.generateShelvesDistributionReport();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategoryPerformance>> captor = ArgumentCaptor.forClass(List.class);
        verify(excelService).generateReport(captor.capture(), eq("West"));
        List<CategoryPerformance> categories = captor.getValue();

        assertThat(categories).hasSize(2);
        CategoryPerformance toys = categories.stream().filter(c -> c.category().equals("Toys")).findFirst().orElseThrow();
        assertThat(toys.discountSales()).isEqualByComparingTo("150");
        assertThat(toys.costSales()).isEqualByComparingTo("80");
        assertThat(toys.currentCostBalance()).isEqualByComparingTo("15");

        CategoryPerformance books = categories.stream().filter(c -> c.category().equals("Books")).findFirst().orElseThrow();
        assertThat(books.discountSales()).isEqualByComparingTo("30");
        assertThat(books.costSales()).isEqualByComparingTo("10");
        assertThat(books.currentCostBalance()).isEqualByComparingTo("2");
    }

    @Test
    void generateShelvesDistributionReport_groupedByStore_passesRawRowsThrough() throws Exception {
        when(services.stream()).thenReturn(Stream.of(storeCategoryService));
        when(storeCategoryService.getGranularity()).thenReturn(Granularity.STORES);
        List<StoreCategoryPerformance> rows = List.of(
                new StoreCategoryPerformance("Store A", "Toys", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO));
        when(storeCategoryService.getStoreCategoryPerformance()).thenReturn(rows);
        ShelvesDistributionProperties properties = new ShelvesDistributionProperties(true, Granularity.STORES, null, List.of());
        ShelvesDistributionService service = new ShelvesDistributionService(properties, services, excelService);

        service.generateShelvesDistributionReport();

        verify(excelService).generateReport(rows);
    }
}
