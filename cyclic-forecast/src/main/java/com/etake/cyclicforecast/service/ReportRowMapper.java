package com.etake.cyclicforecast.service;

import com.etake.cyclicforecast.config.properties.CyclicForecastProperties;
import com.etake.cyclicforecast.model.ForecastResult;
import com.etake.cyclicforecast.model.NamedRef;
import com.etake.cyclicforecast.model.PlanningScopeRow;
import com.etake.cyclicforecast.model.ProductGroup;
import com.etake.cyclicforecast.model.ReportRow;
import com.etake.cyclicforecast.repository.LocationRepository;
import com.etake.cyclicforecast.repository.ProductHierarchyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportRowMapper {
    private final ProductHierarchyRepository productHierarchyRepository;
    private final LocationRepository locationRepository;
    private final CyclicForecastProperties properties;

    public List<ReportRow> map(final List<ForecastResult> results) {
        final Map<String, ProductGroup> productGroups = results.stream()
                .map(result -> result.row().productId())
                .distinct()
                .map(productHierarchyRepository::findGroup)
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(ProductGroup::productId, Function.identity()));

        final List<String> storeIds = results.stream()
                .map(result -> result.row().storeId())
                .distinct()
                .toList();
        final Map<String, String> storeNames = locationRepository.findNamesByIds(storeIds).stream()
                .collect(Collectors.toMap(NamedRef::id, NamedRef::name));

        return results.stream()
                .map(result -> toRow(result, productGroups, storeNames))
                .toList();
    }

    private ReportRow toRow(final ForecastResult result,
                             final Map<String, ProductGroup> productGroups,
                             final Map<String, String> storeNames) {
        final PlanningScopeRow row = result.row();
        final ProductGroup group = productGroups.get(row.productId());
        return new ReportRow(
                group != null ? group.manager() : null,
                storeNames.getOrDefault(row.storeId(), row.storeId()),
                row.storeFormat(),
                row.productId(),
                group != null ? group.productName() : null,
                group != null ? group.thirdSubcategoryName() : null,
                row.promotionType(),
                row.carryover(),
                result.level().display(),
                result.donorKey() != null ? result.donorKey().toString() : null,
                result.avgDailySales(),
                forecastQuantity(result.avgDailySales(), row.carryover())
        );
    }

    private Long forecastQuantity(final BigDecimal avgDailySales, final Long carryover) {
        if (avgDailySales == null) {
            return null;
        }
        final BigDecimal carryoverValue = carryover != null ? BigDecimal.valueOf(carryover) : BigDecimal.ZERO;
        return avgDailySales.multiply(BigDecimal.valueOf(properties.demandDays()))
                .add(carryoverValue)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
