package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.config.SystemConfigurationProperties;
import com.etake.avgcheckplan.model.NamedRef;
import com.etake.avgcheckplan.model.StoreAvgCheck;
import com.etake.avgcheckplan.model.StoreCheckPlanRecord;
import com.etake.avgcheckplan.repository.LocationRepository;
import com.etake.avgcheckplan.repository.StoreChecksPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreChecksPlanUploadService {
    private final SystemConfigurationProperties systemConfigurationProperties;
    private final AvgItemsPerCheckFileService avgItemsPerCheckFileService;
    private final LocationRepository locationRepository;
    private final StoreChecksPlanRepository storeChecksPlanRepository;

    public void upload(final List<StoreAvgCheck> positions) {
        final LocalDate asOfDate = systemConfigurationProperties.dateRange().fromDate();
        final Map<String, BigDecimal> avgItemsPerCheckByStore = avgItemsPerCheckFileService.readAvgItemsPerCheck();

        final Map<String, String> storeIdByName = locationRepository
                .findStoresByName(positions.stream().map(StoreAvgCheck::store).toList(), asOfDate)
                .stream()
                .collect(toMap(NamedRef::name, NamedRef::id));

        final List<StoreCheckPlanRecord> records = positions.stream()
                .map(position -> {
                    final String storeId = storeIdByName.get(position.store());
                    if (storeId == null) {
                        log.warn("Skipping {}: no matching open location found", position.store());
                        return null;
                    }
                    final BigDecimal avgItemsPerCheck = avgItemsPerCheckByStore.getOrDefault(position.store(), BigDecimal.ZERO);
                    return new StoreCheckPlanRecord(storeId, asOfDate.getYear(), asOfDate.getMonthValue(),
                            position.avgCheck(), avgItemsPerCheck);
                })
                .filter(Objects::nonNull)
                .toList();

        storeChecksPlanRepository.replaceMonthlyPlans(asOfDate.getYear(), asOfDate.getMonthValue(), records);
    }
}
