package com.etake.shelvesdistribution.service;

import com.etake.shelvesdistribution.model.StoreCategoryPerformance;
import com.etake.shelvesdistribution.model.enumeration.Granularity;

import java.util.List;

public interface StoreCategoryService {

    List<StoreCategoryPerformance> getStoreCategoryPerformance();

    Granularity getGranularity();
}
