package com.etake.turnoverplan.repository;

import com.etake.turnoverplan.model.StoreCategory;

import java.util.List;

public interface StoreCategoryRepository {

    List<StoreCategory> getActiveStoreCategoryPairs();
}
