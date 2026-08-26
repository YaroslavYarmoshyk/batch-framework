package com.etake.avgcheckplan.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

@Component
public class SimilarStoreResolver {

    public Optional<String> findClosestStore(final BigDecimal referenceDynamic, final Map<String, BigDecimal> candidateDynamics) {
        return candidateDynamics.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().subtract(referenceDynamic).abs()))
                .map(Map.Entry::getKey);
    }
}
