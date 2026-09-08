package com.etake.cyclicforecast.service;

import com.etake.cyclicforecast.config.properties.CyclicForecastProperties;
import com.etake.cyclicforecast.model.PlanningScopeRow;
import com.etake.cyclicforecast.repository.PlanningScopeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanningScopeService {
    private final PlanningScopeRepository planningScopeRepository;
    private final CyclicForecastProperties properties;

    public List<PlanningScopeRow> loadPlanningScope() {
        final var scope = properties.planningScope();
        return planningScopeRepository.loadPlanningScope(scope.fromDate(), scope.toDate(), scope.promotionType());
    }
}
