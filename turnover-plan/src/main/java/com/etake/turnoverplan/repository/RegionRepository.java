package com.etake.turnoverplan.repository;

import com.etake.turnoverplan.model.RegionOrder;

import java.util.List;

public interface RegionRepository {

    List<RegionOrder> findAllRegionOrders();
}
