package com.etake.shelvesdistribution.service;

import com.etake.shelvesdistribution.config.properties.ShelvesDistributionProperties;
import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import com.etake.shelvesdistribution.model.enumeration.Granularity;
import com.etake.shelvesdistribution.repository.StoreCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionalStoreCategoryBreakdown implements StoreCategoryService {
    private final StoreCategoryRepository storeCategoryRepository;
    private final ShelvesDistributionProperties shelvesDistributionProperties;

    @Override
    public List<StoreCategoryPerformance> getStoreCategoryPerformance() {
        return storeCategoryRepository.getStoreCategoryPerformance(shelvesDistributionProperties.region());
    }

    @Override
    public Granularity getGranularity() {
        return Granularity.REGION;
    }
}
